export default function MachineCard({ machine, isAnalyzing, hasPendingDecision, onSimulate, onOpenPanel }) {
  const isRunning = machine.status === 'RUNNING'
  const isDown = machine.status === 'DOWN'
  const heartbeatAge = Math.round((Date.now() - machine.lastHeartbeat) / 1000)

  return (
    <div className={`rounded-xl border p-4 transition-all duration-500 ${
      isDown
        ? 'border-red-500/60 bg-red-950/30'
        : isAnalyzing
        ? 'border-amber-500/60 bg-amber-950/20'
        : hasPendingDecision
        ? 'border-purple-500/60 bg-purple-950/20'
        : 'border-slate-700 bg-slate-800/50 hover:border-slate-600'
    }`}>
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2.5">
          <span className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${
            isRunning && !isAnalyzing ? 'bg-green-500 animate-pulse' :
            isDown ? 'bg-red-500' : 'bg-amber-400 animate-pulse'
          }`} />
          <span className="font-bold text-white text-base">{machine.id}</span>
        </div>
        <span className={`text-xs px-2 py-0.5 rounded-full font-semibold tracking-wide ${
          isRunning ? 'bg-green-500/15 text-green-400 border border-green-500/30' :
                      'bg-red-500/15 text-red-400 border border-red-500/30'
        }`}>
          {machine.status}
        </span>
      </div>

      <p className="text-xs text-slate-500 mb-3">
        {isRunning
          ? `Last heartbeat ${heartbeatAge}s ago`
          : 'Heartbeat lost'}
      </p>

      {isAnalyzing && (
        <div className="flex items-center gap-2 text-amber-400 text-xs mb-3 bg-amber-950/40 rounded-lg px-2.5 py-1.5">
          <svg className="animate-spin w-3 h-3 flex-shrink-0" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          <span>Claude is analyzing…</span>
        </div>
      )}

      {hasPendingDecision && !isAnalyzing && (
        <button
          onClick={() => onOpenPanel(machine.id)}
          className="w-full text-xs py-2 px-3 rounded-lg border border-purple-500/60 text-purple-300
            bg-purple-950/30 hover:bg-purple-900/40 hover:border-purple-400/70 hover:text-purple-200
            transition-all duration-200 flex items-center justify-center gap-1.5 mb-2">
          <span>🤖</span>
          <span>View Claude Analysis</span>
        </button>
      )}

      {isRunning && !isAnalyzing && !hasPendingDecision && (
        <button
          onClick={() => onSimulate(machine.id)}
          className="w-full text-xs py-2 px-3 rounded-lg border border-slate-600 text-slate-400
            hover:border-red-500/70 hover:text-red-400 hover:bg-red-950/20 transition-all duration-200">
          ⚠ Simulate Failure
        </button>
      )}
    </div>
  )
}
