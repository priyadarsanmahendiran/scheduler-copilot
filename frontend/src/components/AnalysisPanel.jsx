import GanttChart from './GanttChart'

function parseAnalysis(text) {
  if (!text) return {}
  const extract = (pattern) => {
    const m = text.match(pattern)
    return m ? m[1].trim() : ''
  }
  return {
    situation:      extract(/SITUATION:\s*([\s\S]*?)(?=\n\nOPTION A|OPTION A|$)/),
    optionA:        extract(/OPTION A \(?Fast\)?:?\s*([\s\S]*?)(?=\n\nOPTION B|OPTION B|$)/),
    optionB:        extract(/OPTION B \(?Cheap\)?:?\s*([\s\S]*?)(?=\n\nCASCADE|CASCADE|$)/),
    cascade:        extract(/CASCADE RISK:\s*([\s\S]*?)(?=\n\nMY RECOMMEND|MY RECOMMEND|$)/),
    recommendation: extract(/MY RECOMMENDATION:\s*([\s\S]*?)(?=\n\n|$)/),
  }
}

function Spinner() {
  return (
    <div className="w-10 h-10 border-2 border-purple-500 border-t-transparent rounded-full animate-spin" />
  )
}

function MetricBadge({ label, value, color }) {
  return (
    <div className="flex items-center justify-between py-1">
      <span className="text-xs text-slate-500">{label}</span>
      <span className={`text-xs font-bold ${color}`}>{value}</span>
    </div>
  )
}

export default function AnalysisPanel({ decision, analyzing, analyzingId, onChoose, onDismiss }) {
  const sections = parseAnalysis(decision?.claudeAnalysis)

  return (
    <aside className="w-[420px] flex-shrink-0 border-l border-slate-800 bg-slate-900 flex flex-col animate-slide-in">
      {/* Header */}
      <div className="flex items-center justify-between px-5 py-3.5 border-b border-slate-800 flex-shrink-0">
        <div className="flex items-center gap-2">
          <span className="text-purple-400 text-base">🤖</span>
          <span className="text-sm font-semibold text-white">Claude Analysis</span>
          {analyzingId && (
            <span className="text-xs text-slate-500">— {analyzingId}</span>
          )}
        </div>
        <button
          onClick={onDismiss}
          className="text-slate-500 hover:text-slate-300 text-xl leading-none transition-colors w-6 h-6 flex items-center justify-center">
          ×
        </button>
      </div>

      {/* Loading */}
      {analyzing && (
        <div className="flex-1 flex flex-col items-center justify-center gap-5 p-8 text-center">
          <Spinner />
          <div>
            <p className="text-white font-semibold">Analyzing {analyzingId} failure</p>
            <p className="text-slate-400 text-sm mt-2 leading-relaxed">
              OR-Tools solving time-optimal<br />
              and cost-optimal schedules…
            </p>
            <p className="text-purple-400/70 text-sm mt-2">
              Claude assessing cascade risks…
            </p>
          </div>
        </div>
      )}

      {/* Content */}
      {!analyzing && decision && (
        <div className="flex-1 overflow-y-auto">
          <div className="p-4 space-y-4">

            {/* Situation */}
            {sections.situation && (
              <div className="bg-slate-800/60 rounded-xl p-3.5 border border-slate-700/60">
                <div className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-1.5">
                  📋 Situation
                </div>
                <p className="text-sm text-slate-200 leading-relaxed">{sections.situation}</p>
              </div>
            )}

            {/* Options side by side */}
            <div className="grid grid-cols-2 gap-3">
              {/* Option A */}
              <div className="bg-blue-950/40 rounded-xl p-3.5 border border-blue-800/40 flex flex-col gap-2">
                <div className="flex items-center gap-1.5">
                  <span className="text-blue-400 font-black text-xs">⚡ A</span>
                  <span className="text-xs font-bold text-blue-300">Fast Track</span>
                </div>
                {decision.optionAMetrics && (
                  <div className="border-t border-blue-800/30 pt-2">
                    <MetricBadge label="Makespan" value={`${decision.optionAMetrics.makespan}m`} color="text-blue-300" />
                    <MetricBadge label="Disrupted" value={`${decision.optionAMetrics.disruptedCount} machines`} color="text-blue-300" />
                  </div>
                )}
                <p className="text-xs text-slate-400 leading-relaxed">{decision.optionAText}</p>
              </div>

              {/* Option B */}
              <div className="bg-emerald-950/40 rounded-xl p-3.5 border border-emerald-800/40 flex flex-col gap-2">
                <div className="flex items-center gap-1.5">
                  <span className="text-emerald-400 font-black text-xs">💰 B</span>
                  <span className="text-xs font-bold text-emerald-300">Cost Optimal</span>
                </div>
                {decision.optionBMetrics && (
                  <div className="border-t border-emerald-800/30 pt-2">
                    <MetricBadge label="Makespan" value={`${decision.optionBMetrics.makespan}m`} color="text-emerald-300" />
                    <MetricBadge label="Disrupted" value={`${decision.optionBMetrics.disruptedCount} machines`} color="text-emerald-300" />
                  </div>
                )}
                <p className="text-xs text-slate-400 leading-relaxed">{decision.optionBText}</p>
              </div>
            </div>

            {/* Proposed Gantt charts */}
            {decision.optionASchedule && (
              <GanttChart
                schedule={decision.optionASchedule}
                title="Option A — Proposed Schedule"
                highlightColor="blue"
              />
            )}
            {decision.optionBSchedule && (
              <GanttChart
                schedule={decision.optionBSchedule}
                title="Option B — Proposed Schedule"
                highlightColor="emerald"
              />
            )}

            {/* Cascade risk */}
            {sections.cascade && (
              <div className="bg-amber-950/30 rounded-xl p-3.5 border border-amber-800/30">
                <div className="text-xs font-bold text-amber-400 uppercase tracking-widest mb-1.5">
                  ⚠️ Cascade Risk
                </div>
                <p className="text-xs text-slate-300 leading-relaxed">{sections.cascade}</p>
              </div>
            )}

            {/* Recommendation */}
            {sections.recommendation && (
              <div className="bg-purple-950/30 rounded-xl p-3.5 border border-purple-800/30">
                <div className="text-xs font-bold text-purple-400 uppercase tracking-widest mb-1.5">
                  🤖 Claude Recommends
                </div>
                <p className="text-sm text-slate-100 leading-relaxed font-medium">
                  {sections.recommendation}
                </p>
              </div>
            )}

            {/* Decision buttons */}
            <div className="pt-2 space-y-2.5">
              <p className="text-xs text-slate-500 text-center">— Operator decision —</p>
              <button
                onClick={() => onChoose('apply option A, the fast time-optimal schedule')}
                className="w-full py-3 px-4 bg-blue-600 hover:bg-blue-500 active:bg-blue-700
                           text-white text-sm font-bold rounded-xl transition-all duration-150
                           shadow-lg shadow-blue-900/40">
                ⚡ Apply Option A — Fast Track
              </button>
              <button
                onClick={() => onChoose('apply option B, the cost-optimal schedule')}
                className="w-full py-3 px-4 bg-emerald-700 hover:bg-emerald-600 active:bg-emerald-800
                           text-white text-sm font-bold rounded-xl transition-all duration-150
                           shadow-lg shadow-emerald-900/40">
                💰 Apply Option B — Cost Optimal
              </button>
            </div>
          </div>
        </div>
      )}
    </aside>
  )
}
