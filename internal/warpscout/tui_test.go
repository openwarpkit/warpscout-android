package warpscout

import (
	"bytes"
	"fmt"
	"os"
	"slices"
	"strings"
	"testing"
	"time"

	tea "github.com/charmbracelet/bubbletea"
)

func TestScanModelFeed(t *testing.T) {
	var m tea.Model = newScanModel(nil, false)

	step := func(msg tea.Msg) { m, _ = m.Update(msg) }

	step(barBeginMsg{label: "Phase 2", total: 3})
	step(foundMsg{endpoint: "a:2408", epPing: 50 * time.Millisecond})
	step(foundMsg{endpoint: "b:2408", epPing: 20 * time.Millisecond})
	step(foundMsg{endpoint: "c:2408", epPing: 0})
	step(foundMsg{endpoint: "d:2408", epPing: 20 * time.Millisecond})
	step(probedMsg{})
	step(probedMsg{})
	step(probedMsg{})

	sm := m.(scanModel)
	var got []string
	for _, r := range sm.feed {
		got = append(got, r.endpoint)
	}
	want := []string{"b:2408", "d:2408", "a:2408", "c:2408"}
	if !slices.Equal(got, want) {
		t.Errorf("feed order = %v, want %v (by ping, then endpoint, unknown last)", got, want)
	}
	if sm.done != 3 {
		t.Errorf("probed = %d, want 3", sm.done)
	}
	if sm.finished {
		t.Error("model finished before doneMsg")
	}

	step(doneMsg{})
	if !m.(scanModel).finished {
		t.Error("doneMsg should mark the model finished")
	}
}

func TestScanModelFitsWindow(t *testing.T) {
	var m tea.Model = newScanModel(nil, false)
	m, _ = m.Update(tea.WindowSizeMsg{Width: 60, Height: 20})
	m, _ = m.Update(barBeginMsg{label: "Phase 2", total: 200})
	for i := 0; i < 200; i++ {
		m, _ = m.Update(foundMsg{
			endpoint: fmt.Sprintf("1.2.3.%d:2408", i),
			epPing:   time.Duration(i) * time.Millisecond,
			exit:     fmt.Sprintf("C%d", i%9),
			colo:     fmt.Sprintf("N%d", i%9),
		})
	}

	lines := strings.Count(m.(scanModel).View(), "\n")
	if lines > 20 {
		t.Errorf("view = %d lines, want <= 20 (window height)", lines)
	}
	if lines < 10 {
		t.Errorf("view = %d lines, wastes a 20-line window", lines)
	}

	m, _ = m.Update(tea.WindowSizeMsg{Width: 60, Height: 60})
	if rows := strings.Count(m.(scanModel).View(), ":2408"); rows != feedMax {
		t.Errorf("feed = %d rows in a tall window, want the %d cap", rows, feedMax)
	}
}

func TestScanModelExpandFeed(t *testing.T) {
	var m tea.Model = newScanModel(nil, false)
	m, _ = m.Update(tea.WindowSizeMsg{Width: 70, Height: 20})
	m, _ = m.Update(stepMsg{done: true, label: "Phase 1", summary: "2408"})
	m, _ = m.Update(barBeginMsg{label: "Phase 2", total: 200})
	for i := 0; i < 60; i++ {
		m, _ = m.Update(foundMsg{
			endpoint: fmt.Sprintf("1.2.3.%d:2408", i),
			epPing:   time.Duration(i) * time.Millisecond,
			exit:     "DE",
			colo:     "FRA",
		})
	}

	before := strings.Count(m.(scanModel).View(), "\n")
	m, _ = m.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune{'f'}})
	v := m.(scanModel).View()

	if strings.Contains(v, "NODES") || strings.Contains(v, "Phase 1") {
		t.Error("f should leave the feed alone on screen")
	}
	if rows := strings.Count(v, ":2408"); rows <= 9 {
		t.Errorf("feed = %d rows after f, want more than the 9 it had", rows)
	}
	if lines := strings.Count(v, "\n"); lines > 20 || lines != before {
		t.Errorf("view = %d lines after f, want %d (window height)", lines, before)
	}
}

func TestScanModelDropsFeedOnDone(t *testing.T) {
	sm := newScanModel(nil, false)
	sm.dropLists = true
	var m tea.Model = sm
	m, _ = m.Update(tea.WindowSizeMsg{Width: 70, Height: 30})
	m, _ = m.Update(foundMsg{endpoint: "1.2.3.4:2408", exit: "DE", colo: "FRA"})
	m, _ = m.Update(barEndMsg{label: "Phase 2", summary: "done"})
	m, _ = m.Update(doneMsg{})

	v := m.(scanModel).View()
	if strings.Contains(v, ":2408") || strings.Contains(v, "NODES") {
		t.Error("the feed and the node panel should go once the report repeats them")
	}
	if !strings.Contains(v, "Phase 2") {
		t.Error("the phase lines should stay")
	}
}

func TestScanModelCounts(t *testing.T) {
	var m tea.Model = newScanModel(nil, false)
	m, _ = m.Update(tea.WindowSizeMsg{Width: 100, Height: 30})
	m, _ = m.Update(barBeginMsg{label: "Phase 2", total: 10})
	m, _ = m.Update(foundMsg{endpoint: "a:2408", exit: "DE", colo: "FRA"})
	m, _ = m.Update(foundMsg{endpoint: "b:2408", exit: "RU", colo: "DME"})
	m, _ = m.Update(foundMsg{endpoint: "c:2408", exit: "NL", colo: "AMS", torn: true})

	if got := m.(scanModel).counts(); got != "   working 2 · torn 1 · nodes 2" {
		t.Errorf("counts() = %q", got)
	}
}

func TestScanModelElapsed(t *testing.T) {
	if got := took(time.Now().Add(-92 * time.Second)); got != " in 1m32s" {
		t.Errorf("took() = %q", got)
	}
	if got := took(time.Time{}); got != "" {
		t.Errorf("took(zero) = %q, want empty", got)
	}
	if got := since(time.Now()); got != "" {
		t.Errorf("since(now) = %q, want empty under a second", got)
	}

	var m tea.Model = newScanModel(nil, false)
	m, _ = m.Update(barBeginMsg{label: "Phase 2", total: 10})
	sm := m.(scanModel)
	sm.barAt = time.Now().Add(-time.Minute)
	m, _ = tea.Model(sm).Update(barEndMsg{label: "Phase 2", summary: "8 working"})

	if lines := m.(scanModel).lines; !strings.Contains(lines[len(lines)-1], "in 1m0s") {
		t.Errorf("phase line = %q, want an elapsed suffix", lines[len(lines)-1])
	}
}

func TestPlainProgress(t *testing.T) {
	var buf bytes.Buffer
	plainOut = &buf
	defer func() { plainOut = os.Stderr }()

	plainEmit(probedMsg{})
	plainEmit(barBeginMsg{label: "Phase 2", total: 25})
	for i := 0; i < 25; i++ {
		plainEmit(probedMsg{})
	}
	plainEmit(barEndMsg{label: "Phase 2", summary: "20 working"})

	want := "Phase 2: 25...\n  2/25\n  4/25\n  6/25\n  8/25\n  10/25\n  12/25\n  14/25\n  16/25\n  18/25\n  20/25\n  22/25\n  24/25\nPhase 2: 20 working\n"
	if buf.String() != want {
		t.Errorf("plain output =\n%q\nwant\n%q", buf.String(), want)
	}
}

func TestScanModelNodes(t *testing.T) {
	var m tea.Model = newScanModel(nil, false)

	step := func(msg tea.Msg) { m, _ = m.Update(msg) }

	step(foundMsg{endpoint: "a:2408", exit: "DE", colo: "FRA"})
	step(foundMsg{endpoint: "b:2408", exit: "RU", colo: "DME"})
	step(foundMsg{endpoint: "c:2408", exit: "RU", colo: "DME"})
	step(foundMsg{endpoint: "d:2408", exit: "NL", colo: "AMS", torn: true})
	step(foundMsg{endpoint: "e:2408"})

	nodes := m.(scanModel).nodes
	want := []nodeStat{{"RU", "DME", 2}, {"DE", "FRA", 1}}
	if len(nodes) != len(want) {
		t.Fatalf("nodes = %v, want %v", nodes, want)
	}
	for i := range want {
		if nodes[i] != want[i] {
			t.Errorf("nodes[%d] = %v, want %v", i, nodes[i], want[i])
		}
	}
}
