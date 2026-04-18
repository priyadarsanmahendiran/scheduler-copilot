import { useState, useEffect, useRef } from 'react'
import MachineCard from './components/MachineCard'
import GanttChart from './components/GanttChart'
import AnalysisPanel from './components/AnalysisPanel'

const ANALYZING = 'ANALYZING'

function Toast({ toast }) {
  if (!toast) return null
  const colors = {
    success: 'bg-green-600',
    error: 'bg-red-600',
    warning: 'bg-amber-600',
    info: 'bg-slate-700',
  }
  return (
    <div className={`fixed bottom-6 right-6 px-4 py-3 rounded-xl text-white text-sm font-medium
      shadow-2xl z-50 animate-fade-in ${colors[toast.type] || colors.info}`}>
      {toast.msg}
    </div>
  )
}

export default function App() {
  const [machines, setMachines] = useState([])
  const [schedule, setSchedule] = useState(null)
  // Map of machineId → sessionId | 'ANALYZING'  (polled from backend every 2s)
  const [pendingSessions, setPendingSessions] = useState({})
  const [decision, setDecision] = useState(null)
  const [analyzingId, setAnalyzingId] = useState(null)
  const [panelOpen, setPanelOpen] = useState(false)
  const [toast, setToast] = useState(null)
  // Job IDs that breach SLA in the currently active schedule
  const [activeSlaBreaches, setActiveSlaBreaches] = useState([])

  const prevPendingRef = useRef({})

  const showToast = (msg, type = 'info') => {
    setToast({ msg, type })
    setTimeout(() => setToast(null), 4000)
  }

  // Subscribe to server-sent events for live state updates.
  // EventSource reconnects automatically on network errors.
  useEffect(() => {
    const es = new EventSource('/api/events')

    es.addEventListener('machines', e => {
      try { setMachines(JSON.parse(e.data)) } catch {}
    })
    es.addEventListener('schedule', e => {
      try { setSchedule(JSON.parse(e.data)) } catch {}
    })
    es.addEventListener('pending', e => {
      try { setPendingSessions(JSON.parse(e.data)) } catch {}
    })

    return () => es.close()
  }, [])

  // Detect external failure events: Swagger / real machine failure.
  // When a machine goes to ANALYZING we open the panel; when it finishes we load the session.
  useEffect(() => {
    const prev = prevPendingRef.current
    prevPendingRef.current = pendingSessions

    for (const [machineId, val] of Object.entries(pendingSessions)) {
      const prevVal = prev[machineId]

      // Machine just entered ANALYZING (triggered externally, not from this browser)
      if (val === ANALYZING && !prevVal) {
        setAnalyzingId(machineId)
        setDecision(null)
        setPanelOpen(true)
        setActiveSlaBreaches([])
      }

      // Machine completed analysis (ANALYZING → real session ID)
      if (val && val !== ANALYZING && prevVal === ANALYZING) {
        fetch(`/api/schedule/session/${val}`)
          .then(r => r.ok ? r.json() : null)
          .then(data => {
            if (data) {
              setDecision(data)
              setAnalyzingId(machineId)
              setPanelOpen(true)
            }
          })
          .catch(() => {})
      }
    }
  }, [pendingSessions])

  // Triggered by the "Simulate Failure" button in the UI.
  // The backend will immediately write 'ANALYZING' to pendingSessions, so the poll
  // will reflect analyzing state even while this fetch is awaiting.
  const simulateFailure = async (machineId) => {
    setAnalyzingId(machineId)
    setDecision(null)
    setPanelOpen(true)
    setActiveSlaBreaches([])

    try {
      await fetch(`/machines/${machineId}/down`, { method: 'POST' })

      const res = await fetch('/api/schedule/failure', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ machineId }),
      })
      if (!res.ok) throw new Error(`Analysis failed (${res.status})`)
      setDecision(await res.json())
    } catch (e) {
      showToast(e.message || 'Failed to analyze failure', 'error')
      setPanelOpen(false)
      setAnalyzingId(null)
    }
  }

  const openPanel = async (machineId) => {
    if (analyzingId === machineId && decision) {
      setPanelOpen(true)
      return
    }
    const sessionId = pendingSessions[machineId]
    if (!sessionId || sessionId === ANALYZING) return
    try {
      const res = await fetch(`/api/schedule/session/${sessionId}`)
      if (!res.ok) throw new Error(`${res.status}`)
      const data = await res.json()
      setDecision(data)
      setAnalyzingId(machineId)
      setPanelOpen(true)
    } catch (e) {
      showToast('Failed to load analysis', 'error')
    }
  }

  const submitChoice = async (userMessage) => {
    if (!decision) return
    try {
      const res = await fetch('/api/schedule/choose', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId: decision.sessionId, userMessage }),
      })
      const result = await res.json()

      if (result.applied) {
        const isOptionA = userMessage.toLowerCase().includes('option a')
        const sla = isOptionA ? decision.optionASla : decision.optionBSla
        setActiveSlaBreaches(sla?.breachedJobIds || [])
        showToast('✓ Schedule updated successfully', 'success')
        setDecision(null)
        setAnalyzingId(null)
        setPanelOpen(false)
      } else if (result.needsClarification) {
        showToast(result.clarificationMessage, 'warning')
      }
    } catch (e) {
      showToast('Failed to submit choice', 'error')
    }
  }

  const simulateDegradation = async (machineId) => {
    try {
      await fetch(`/machines/${machineId}/degrade`, { method: 'POST' })
      showToast(`Degradation simulation started for ${machineId}`, 'warning')
    } catch {
      showToast('Failed to start degradation simulation', 'error')
    }
  }

  const dismissPanel = () => setPanelOpen(false)

  // analyzing = backend is still running CP-SAT + Claude for the tracked machine,
  //             AND we don't already have a decision loaded (decision takes priority)
  const analyzing = !!analyzingId
    && pendingSessions[analyzingId] === ANALYZING
    && !decision

  const downCount = machines.filter(m => m.status === 'DOWN').length
  const systemOk = downCount === 0

  return (
    <div className="h-screen bg-slate-950 text-slate-100 flex flex-col overflow-hidden">
      {/* Header */}
      <header className="flex-shrink-0 border-b border-slate-800 bg-slate-900/80 backdrop-blur px-6 py-3
                         flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="text-2xl">🏭</span>
          <div>
            <h1 className="text-base font-bold text-white leading-tight">Scheduler CoPilot</h1>
            <p className="text-xs text-slate-500">AI-Driven Factory Shop Floor Management</p>
          </div>
        </div>

        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2">
            <span className={`w-2 h-2 rounded-full ${systemOk ? 'bg-green-500 animate-pulse' : 'bg-red-500'}`} />
            <span className="text-sm text-slate-300">
              {systemOk ? 'All Systems Nominal' : `${downCount} Machine${downCount > 1 ? 's' : ''} Down`}
            </span>
          </div>
          <div className="text-xs text-slate-600 border border-slate-800 rounded-lg px-2.5 py-1">
            OR-Tools + Claude
          </div>
        </div>
      </header>

      {/* Body */}
      <div className="flex flex-1 overflow-hidden">
        {/* Machines sidebar */}
        <aside className="w-72 flex-shrink-0 border-r border-slate-800 bg-slate-900/50 overflow-y-auto p-4">
          <h2 className="text-xs font-bold text-slate-500 uppercase tracking-widest mb-4">
            Machines ({machines.length})
          </h2>
          <div className="space-y-3">
            {machines.length === 0 && (
              <p className="text-xs text-slate-600 text-center py-8">Connecting to backend…</p>
            )}
            {machines.map(m => (
              <MachineCard
                key={m.id}
                machine={m}
                isAnalyzing={pendingSessions[m.id] === ANALYZING}
                hasPendingDecision={pendingSessions[m.id] && pendingSessions[m.id] !== ANALYZING}
                onSimulate={simulateFailure}
                onOpenPanel={openPanel}
                onDegrade={simulateDegradation}
              />
            ))}
          </div>

          {/* Legend */}
          <div className="mt-6 pt-4 border-t border-slate-800 space-y-2">
            <p className="text-xs font-bold text-slate-500 uppercase tracking-widest mb-3">Status</p>
            {[
              { color: 'bg-green-500', label: 'Running' },
              { color: 'bg-red-500', label: 'Down' },
              { color: 'bg-amber-400', label: 'Analyzing' },
              { color: 'bg-purple-500', label: 'Pending decision' },
            ].map(({ color, label }) => (
              <div key={label} className="flex items-center gap-2">
                <span className={`w-2 h-2 rounded-full ${color}`} />
                <span className="text-xs text-slate-500">{label}</span>
              </div>
            ))}
          </div>
        </aside>

        {/* Gantt center */}
        <main className="flex-1 overflow-auto p-6 min-w-0">
          <div className="flex items-center justify-between mb-5">
            <div>
              <h2 className="text-xs font-bold text-slate-400 uppercase tracking-widest">
                Current Schedule
              </h2>
              {analyzingId && (
                <p className="text-xs text-amber-400 mt-0.5">
                  {analyzing
                    ? `Rescheduling ${analyzingId} jobs…`
                    : `${analyzingId} jobs rescheduled — awaiting operator decision`}
                </p>
              )}
            </div>
            <span className="text-xs text-slate-600">↻ Live — updates every 2s</span>
          </div>

          <GanttChart schedule={schedule} failedMachineId={analyzingId} slaBreaches={activeSlaBreaches} />

          {!schedule && (
            <div className="mt-4 text-center text-slate-600 text-sm">
              Waiting for backend…
            </div>
          )}
        </main>

        {/* Analysis panel */}
        {panelOpen && (analyzing || decision) && (
          <AnalysisPanel
            decision={decision}
            analyzing={analyzing}
            analyzingId={analyzingId}
            onChoose={submitChoice}
            onDismiss={dismissPanel}
          />
        )}
      </div>

      <Toast toast={toast} />
    </div>
  )
}
