import { useState, useEffect } from 'react'
import MachineCard from './components/MachineCard'
import GanttChart from './components/GanttChart'
import AnalysisPanel from './components/AnalysisPanel'

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
  const [decision, setDecision] = useState(null)
  const [analyzing, setAnalyzing] = useState(false)
  const [analyzingId, setAnalyzingId] = useState(null)
  const [toast, setToast] = useState(null)

  const showToast = (msg, type = 'info') => {
    setToast({ msg, type })
    setTimeout(() => setToast(null), 4000)
  }

  // Poll machines and schedule every 2s
  useEffect(() => {
    const poll = async () => {
      try {
        const [mRes, sRes] = await Promise.all([
          fetch('/machines'),
          fetch('/schedule'),
        ])
        if (mRes.ok) setMachines(await mRes.json())
        if (sRes.ok) setSchedule(await sRes.json())
      } catch {
        // backend not ready yet, retry silently
      }
    }
    poll()
    const id = setInterval(poll, 2000)
    return () => clearInterval(id)
  }, [])

  const simulateFailure = async (machineId) => {
    setAnalyzing(true)
    setAnalyzingId(machineId)
    setDecision(null)

    try {
      // Block the heartbeat so FailureDetectionService marks it DOWN
      await fetch(`/machines/${machineId}/down`, { method: 'POST' })

      // Trigger OR-Tools + Claude analysis directly (no need to wait for auto-detection)
      const res = await fetch('/api/schedule/failure', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ machineId }),
      })
      if (!res.ok) throw new Error(`Analysis failed (${res.status})`)
      setDecision(await res.json())
    } catch (e) {
      showToast(e.message || 'Failed to analyze failure', 'error')
      setAnalyzing(false)
      setAnalyzingId(null)
    } finally {
      setAnalyzing(false)
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
        showToast('✓ Schedule updated successfully', 'success')
        setDecision(null)
        setAnalyzingId(null)
      } else if (result.needsClarification) {
        showToast(result.clarificationMessage, 'warning')
      }
    } catch (e) {
      showToast('Failed to submit choice', 'error')
    }
  }

  const dismissPanel = () => {
    setDecision(null)
    setAnalyzingId(null)
  }

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
                isAnalyzing={analyzingId === m.id && analyzing}
                hasPendingDecision={analyzingId === m.id && !!decision}
                onSimulate={simulateFailure}
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
                  {analyzing ? `Rescheduling ${analyzingId} jobs…` : `${analyzingId} jobs rescheduled — awaiting operator decision`}
                </p>
              )}
            </div>
            <span className="text-xs text-slate-600">↻ Live — updates every 2s</span>
          </div>

          <GanttChart
            schedule={schedule}
            failedMachineId={analyzingId}
          />

          {!schedule && (
            <div className="mt-4 text-center text-slate-600 text-sm">
              Waiting for backend…
            </div>
          )}
        </main>

        {/* Analysis panel */}
        {(analyzing || decision) && (
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
