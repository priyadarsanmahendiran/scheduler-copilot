const PALETTE = [
  '#3b82f6', '#8b5cf6', '#22c55e', '#f59e0b',
  '#ec4899', '#14b8a6', '#f97316', '#a78bfa',
]

function colorFor(jobId) {
  const hash = [...(jobId || 'x')].reduce((acc, c) => acc + c.charCodeAt(0), 0)
  return PALETTE[hash % PALETTE.length]
}

function computeBars(assignments) {
  const totals = Object.values(assignments).map(jobs =>
    jobs.reduce((s, j) => s + j.duration, 0)
  )
  const maxTime = Math.max(...totals, 1)

  const bars = {}
  for (const [machineId, jobs] of Object.entries(assignments)) {
    let t = 0
    bars[machineId] = jobs.map(job => {
      const bar = {
        job,
        startPct: (t / maxTime) * 100,
        widthPct: (job.duration / maxTime) * 100,
        color: colorFor(job.id),
      }
      t += job.duration
      return bar
    })
  }
  return { bars, maxTime }
}

function GanttRow({ machineId, bars, isFailed, label, slaBreachSet }) {
  return (
    <div className="flex items-center gap-3">
      <div className={`w-14 text-sm font-bold flex-shrink-0 text-right ${
        isFailed ? 'text-red-400' : 'text-slate-300'
      }`}>
        {label || machineId}
      </div>

      <div className={`relative flex-1 h-11 rounded-lg overflow-hidden ${
        isFailed
          ? 'bg-red-950/30 border border-red-900/40'
          : 'bg-slate-700/40 border border-slate-700/60'
      }`}>
        {isFailed ? (
          <div className="absolute inset-0 flex items-center justify-center gap-2 text-xs text-red-500/80 font-medium">
            <span className="animate-pulse">●</span>
            <span>FAILED — JOBS RESCHEDULED</span>
          </div>
        ) : (
          bars.map(({ job, startPct, widthPct, color }) => {
            const breached = slaBreachSet?.has(job.id)
            return (
              <div
                key={job.id}
                className={`absolute top-1.5 bottom-1.5 rounded-md flex items-center justify-center gap-0.5
                             text-xs font-semibold text-white shadow-md transition-all duration-500
                             ${breached ? 'ring-2 ring-inset ring-red-300/70' : ''}`}
                style={{
                  left: `${startPct}%`,
                  width: `max(${widthPct}%, 28px)`,
                  backgroundColor: breached ? '#b91c1c' : color,
                }}
                title={breached
                  ? `${job.id} — ${job.duration}min ⚠ SLA BREACH (deadline: ${job.deadline}min)`
                  : `${job.id} — ${job.duration}min`}
              >
                {breached && <span className="text-[10px] leading-none">⚠</span>}
                {widthPct > 7 ? job.id : (breached ? '' : '')}
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}

export default function GanttChart({ schedule, failedMachineId, failedMachineIds, title, highlightColor, slaBreaches }) {
  if (!schedule?.assignments || Object.keys(schedule.assignments).length === 0) {
    return (
      <div className={`rounded-xl border p-8 flex items-center justify-center text-slate-500 text-sm ${
        highlightColor === 'blue' ? 'border-blue-800/50 bg-blue-950/20' :
        highlightColor === 'emerald' ? 'border-emerald-800/50 bg-emerald-950/20' :
        'border-slate-700 bg-slate-800/30'
      }`}>
        No schedule data
      </div>
    )
  }

  const slaBreachSet = slaBreaches?.length ? new Set(slaBreaches) : null
  const { bars, maxTime } = computeBars(schedule.assignments)
  const numId = s => parseInt(s.replace(/\D/g, ''), 10) || 0
  const machines = Object.keys(schedule.assignments).sort((a, b) => numId(a) - numId(b))

  const tickCount = 5
  const rawInterval = maxTime / tickCount
  const interval = Math.max(Math.ceil(rawInterval / 10) * 10, 10)
  const ticks = []
  for (let t = 0; t <= maxTime + interval; t += interval) {
    if (t <= maxTime + 1) ticks.push(t)
  }

  const allJobs = Object.values(schedule.assignments).flat()

  return (
    <div className={`rounded-xl border p-5 ${
      highlightColor === 'blue' ? 'border-blue-700/50 bg-blue-950/20' :
      highlightColor === 'emerald' ? 'border-emerald-700/50 bg-emerald-950/20' :
      'border-slate-700 bg-slate-800/30'
    }`}>
      {title && (
        <h3 className={`text-xs font-bold uppercase tracking-widest mb-4 ${
          highlightColor === 'blue' ? 'text-blue-400' :
          highlightColor === 'emerald' ? 'text-emerald-400' :
          'text-slate-400'
        }`}>
          {title}
        </h3>
      )}

      {/* Time axis */}
      <div className="relative mb-1 ml-[68px]">
        {ticks.map(t => (
          <span
            key={t}
            className="absolute text-xs text-slate-500 -translate-x-1/2"
            style={{ left: `${(t / maxTime) * 100}%` }}
          >
            {t}m
          </span>
        ))}
      </div>
      <div className="relative mb-3 ml-[68px] h-px bg-slate-700/60">
        {ticks.map(t => (
          <div
            key={t}
            className="absolute top-0 w-px h-2 bg-slate-600"
            style={{ left: `${(t / maxTime) * 100}%` }}
          />
        ))}
      </div>

      {/* Rows */}
      <div className="space-y-2">
        {machines.map(machineId => (
          <GanttRow
            key={machineId}
            machineId={machineId}
            bars={bars[machineId] || []}
            isFailed={failedMachineIds ? failedMachineIds.has(machineId) : machineId === failedMachineId}
            slaBreachSet={slaBreachSet}
          />
        ))}
      </div>

      {/* SLA breach summary */}
      {slaBreachSet && slaBreachSet.size > 0 && (
        <div className="mt-3 flex items-center gap-2 px-3 py-2 bg-red-950/40 border border-red-800/40 rounded-lg">
          <span className="text-red-400 text-sm">⚠</span>
          <span className="text-xs text-red-300 font-semibold">
            SLA breach{slaBreachSet.size > 1 ? 'es' : ''} in applied schedule:
          </span>
          <span className="text-xs text-red-300/80">{[...slaBreachSet].join(', ')}</span>
        </div>
      )}

      {/* Legend */}
      {allJobs.length > 0 && (
        <div className="flex flex-wrap gap-3 mt-4 pt-3 border-t border-slate-700/60">
          {allJobs.map(job => {
            const breached = slaBreachSet?.has(job.id)
            return (
              <div key={job.id} className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-sm flex-shrink-0"
                      style={{ backgroundColor: breached ? '#b91c1c' : colorFor(job.id) }} />
                <span className={`text-xs ${breached ? 'text-red-400 font-semibold' : 'text-slate-400'}`}>
                  {breached && '⚠ '}{job.id} ({job.duration}m)
                </span>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
