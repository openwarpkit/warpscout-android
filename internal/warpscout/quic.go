package warpscout

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"fmt"
)

const (
	quicVersion1     = 1
	quicTagLen       = 16
	quicHelloRandLen = 32
	quicSampleEnd    = 20
	tlsHandshakeHead = 6
)

var quicInitialSalt = []byte{
	0x38, 0x76, 0x2c, 0xf7, 0xf5, 0x59, 0x34, 0xb3, 0x4d, 0x17,
	0x9a, 0xe6, 0xa4, 0xc8, 0x0c, 0xad, 0xcc, 0xbb, 0x7f, 0x0a,
}

func genQUICI1(sni string) (string, []byte, error) {
	dcid := randomBytes(1)
	pkn := []byte{0}
	hello := tlsClientHello(sni)
	payload := quicCryptoFrame(hello)

	packet, headerLen, err := quicInitialPacket(dcid, pkn, payload)
	if err != nil {
		return "", nil, err
	}
	parts := quicCutParts(len(payload)-len(hello), len(hello), len(pkn), headerLen)
	return quicToCPS(packet, parts), packet, nil
}

func quicInitialPacket(dcid, pkn, payload []byte) ([]byte, int, error) {
	// No PADDING frame: the ClientHello already puts the packet well over the
	// 20-byte minimum the header protection sample needs.
	header := []byte{0xC0 | byte(len(pkn)-1)}
	header = binary.BigEndian.AppendUint32(header, quicVersion1)
	header = append(header, quicStr8(dcid)...)
	header = append(header, 0, 0) // empty SCID, empty token
	header = append(header, quicVarint(len(pkn)+len(payload)+quicTagLen)...)
	header = append(header, pkn...)
	headerLen := len(header)

	key, iv, hp := quicInitialKeys(dcid)
	for i := range pkn {
		iv[len(iv)-len(pkn)+i] ^= pkn[i]
	}

	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, 0, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, 0, err
	}
	ciphertext := gcm.Seal(nil, iv, payload, header)

	mask, err := quicHeaderMask(hp, ciphertext[4-len(pkn):quicSampleEnd-len(pkn)])
	if err != nil {
		return nil, 0, err
	}
	header[0] ^= mask[0] & 0x0f
	for i := range pkn {
		header[headerLen-len(pkn)+i] ^= mask[1+i]
	}
	return append(header, ciphertext...), headerLen, nil
}

func quicInitialKeys(dcid []byte) (key, iv, hp []byte) {
	initial := quicHMAC(quicInitialSalt, dcid)
	client := quicExpandLabel(initial, "client in", sha256.Size)
	return quicExpandLabel(client, "quic key", 16),
		quicExpandLabel(client, "quic iv", 12),
		quicExpandLabel(client, "quic hp", 16)
}

// HKDF-Expand-Label truncated to one HMAC block - every QUIC secret fits.
func quicExpandLabel(secret []byte, label string, length int) []byte {
	info := binary.BigEndian.AppendUint16(nil, uint16(length))
	info = append(info, quicStr8([]byte("tls13 "+label))...)
	info = append(info, 0, 1)
	return quicHMAC(secret, info)[:length]
}

func quicHMAC(key, data []byte) []byte {
	m := hmac.New(sha256.New, key)
	m.Write(data)
	return m.Sum(nil)
}

func quicHeaderMask(hp, sample []byte) ([]byte, error) {
	block, err := aes.NewCipher(hp)
	if err != nil {
		return nil, err
	}
	mask := make([]byte, aes.BlockSize)
	block.Encrypt(mask, sample)
	return mask, nil
}

func quicCryptoFrame(data []byte) []byte {
	frame := []byte{0x06}
	frame = append(frame, quicVarint(0)...)
	frame = append(frame, quicVarint(len(data))...)
	return append(frame, data...)
}

func tlsClientHello(sni string) []byte {
	name := []byte(sni)
	serverName := binary.BigEndian.AppendUint16(nil, uint16(len(name)+3))
	serverName = append(serverName, 0)
	serverName = binary.BigEndian.AppendUint16(serverName, uint16(len(name)))
	serverName = append(serverName, name...)

	ext := binary.BigEndian.AppendUint16(nil, 0) // server_name
	ext = binary.BigEndian.AppendUint16(ext, uint16(len(serverName)))
	ext = append(ext, serverName...)

	body := []byte{0x03, 0x03}
	body = append(body, randomBytes(quicHelloRandLen)...)
	body = append(body, 0, 0, 0, 0) // no session id, no cipher suites, no compression
	body = binary.BigEndian.AppendUint16(body, uint16(len(ext)))
	body = append(body, ext...)

	hello := []byte{0x01, byte(len(body) >> 16), byte(len(body) >> 8), byte(len(body))}
	return append(hello, body...)
}

// Alternating keep/randomize byte counts over the finished packet: the static
// prefix must cover the header protection sample, so the first cut never starts
// before ciphertext byte 20-len(pkn).
func quicCutParts(frameHeaderLen, helloLen, pknLen, headerLen int) []int {
	keep := frameHeaderLen + tlsHandshakeHead
	randomized := quicHelloRandLen
	if short := quicSampleEnd - pknLen - keep; short > 0 {
		keep += short
		randomized -= short
	}
	return []int{headerLen + keep, randomized, helloLen - tlsHandshakeHead - quicHelloRandLen, quicTagLen}
}

func quicToCPS(packet []byte, parts []int) string {
	c := &cpsChain{}
	offset, keep := 0, true
	for _, n := range parts {
		if n > 0 && keep {
			c.bytes(packet[offset : offset+n])
		} else if n > 0 {
			c.tag("r", n)
		}
		offset += n
		keep = !keep
	}
	return c.String()
}

func quicVarint(x int) []byte {
	switch {
	case x < 0x40:
		return []byte{byte(x)}
	case x < 0x4000:
		b := binary.BigEndian.AppendUint16(nil, uint16(x))
		b[0] |= 0x40
		return b
	default:
		b := binary.BigEndian.AppendUint32(nil, uint32(x))
		b[0] |= 0x80
		return b
	}
}

func quicStr8(b []byte) []byte {
	return append([]byte{byte(len(b))}, b...)
}

func randomBytes(n int) []byte {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		panic(fmt.Sprintf("crypto/rand: %v", err))
	}
	return b
}
