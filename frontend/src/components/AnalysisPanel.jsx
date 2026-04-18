import { useState, useRef, useEffect } from 'react'
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

function Spinner({ small }) {
  const size = small ? 'w-4 h-4 border-[1.5px]' : 'w-10 h-10 border-2'
  return (
    <div className={`${size} border-purple-500 border-t-transparent rounded-full animate-spin flex-shrink-0`} />
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

function SlaBadge({ sla }) {
  if (!sla) return null
  if (sla.breachCount === 0) {
    return (
      <div className="flex items-center gap-1 text-[10px] font-semibold text-green-400 mt-1">
        <span>✓</span> All SLAs met
      </div>
    )
  }
  return (
    <div className="flex flex-col gap-0.5 mt-1">
      <div className="flex items-center gap-1 text-[10px] font-semibold text-red-400">
        <span>⚠</span> {sla.breachCount} SLA breach{sla.breachCount > 1 ? 'es' : ''}
      </div>
      <div className="text-[10px] text-red-300/70">
        {sla.breachedJobIds?.join(', ')}
      </div>
    </div>
  )
}

function ChatBubble({ role, content }) {
  const isUser = role === 'user'
  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div className={`max-w-[85%] rounded-2xl px-3.5 py-2.5 text-xs leading-relaxed ${
        isUser
          ? 'bg-blue-600 text-white rounded-br-sm'
          : 'bg-slate-700/80 text-slate-200 rounded-bl-sm border border-slate-600/60'
      }`}>
        {!isUser && (
          <span className="text-purple-400 font-semibold text-[10px] block mb-1">Claude</span>
        )}
        {content}
      </div>
    </div>
  )
}

export default function AnalysisPanel({ decision, analyzing, analyzingId, onChoose, onDismiss }) {
  const sections = parseAnalysis(decision?.claudeAnalysis)

  const [chatMessages, setChatMessages] = useState([])
  const [chatInput, setChatInput] = useState('')
  const [chatLoading, setChatLoading] = useState(false)
  const [showUnavailableDialog, setShowUnavailableDialog] = useState(false)
  const chatEndRef = useRef(null)
  const inputRef = useRef(null)

  // Reset chat and show unavailability dialog when a new decision arrives
  useEffect(() => {
    setChatMessages([])
    setChatInput('')
    if (decision?.claudeUnavailable) {
      setShowUnavailableDialog(true)
    }
  }, [decision?.sessionId])

  // Scroll to bottom on new messages
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [chatMessages])

  const sendChat = async () => {
    const msg = chatInput.trim()
    if (!msg || chatLoading || !decision) return

    setChatMessages(prev => [...prev, { role: 'user', content: msg }])
    setChatInput('')
    setChatLoading(true)

    try {
      const res = await fetch('/api/schedule/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId: decision.sessionId, message: msg }),
      })
      if (!res.ok) throw new Error(`${res.status}`)
      const data = await res.json()
      setChatMessages(prev => [...prev, { role: 'assistant', content: data.reply }])
    } catch (e) {
      setChatMessages(prev => [...prev, {
        role: 'assistant',
        content: `Sorry, I couldn't respond right now (${e.message}). Try again.`,
      }])
    } finally {
      setChatLoading(false)
      inputRef.current?.focus()
    }
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendChat()
    }
  }

  return (
    <aside className="w-[420px] flex-shrink-0 border-l border-slate-800 bg-slate-900 flex flex-col animate-slide-in relative">

      {/* Claude unavailable dialog */}
      {showUnavailableDialog && (
        <div className="absolute inset-0 z-20 flex items-center justify-center bg-slate-950/80 backdrop-blur-sm p-6">
          <div className="bg-slate-900 border border-amber-600/50 rounded-2xl p-6 shadow-2xl max-w-xs w-full">
            <div className="flex items-start gap-3 mb-4">
              <span className="text-2xl flex-shrink-0">⚠️</span>
              <div>
                <h3 className="text-sm font-bold text-amber-400 mb-1">Claude AI Unreachable</h3>
                <p className="text-xs text-slate-300 leading-relaxed">
                  The Claude API could not be reached. The analysis below was generated directly
                  from OR-Tools CP-SAT output and may lack nuanced risk assessment.
                </p>
              </div>
            </div>
            <p className="text-xs text-slate-400 mb-5 leading-relaxed">
                The scheduling options and metrics are mathematically correct.
                Review the Gantt charts and makespan figures to make your decision.
            </p>
            <button
              onClick={() => setShowUnavailableDialog(false)}
              className="w-full py-2.5 px-4 bg-amber-600 hover:bg-amber-500 text-white text-sm
                         font-semibold rounded-xl transition-colors duration-150">
              Understood
            </button>
          </div>
        </div>
      )}

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

      {/* Persistent banner when Claude was unavailable */}
      {!analyzing && decision?.claudeUnavailable && (
        <div className="flex items-center gap-2 px-4 py-2 bg-amber-950/50 border-b border-amber-800/40 flex-shrink-0">
          <span className="text-amber-400 text-xs">⚠</span>
          <p className="text-xs text-amber-300/80">
            OR-Tools analysis only — Claude API was unreachable
          </p>
          <button
            onClick={() => setShowUnavailableDialog(true)}
            className="ml-auto text-[10px] text-amber-400/70 hover:text-amber-300 underline underline-offset-2">
            details
          </button>
        </div>
      )}

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
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Scrollable analysis area */}
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
                <div className={`bg-blue-950/40 rounded-xl p-3.5 flex flex-col gap-2 border ${
                  decision.optionASla?.breachCount > 0 ? 'border-red-700/50' : 'border-blue-800/40'
                }`}>
                  <div className="flex items-center gap-1.5">
                    <span className="text-blue-400 font-black text-xs">⚡ A</span>
                    <span className="text-xs font-bold text-blue-300">Fast Track</span>
                  </div>
                  {decision.optionAMetrics && (
                    <div className="border-t border-blue-800/30 pt-2">
                      <MetricBadge label="Makespan" value={`${decision.optionAMetrics.makespan}m`} color="text-blue-300" />
                      <MetricBadge label="Disrupted" value={`${decision.optionAMetrics.disruptedCount} machines`} color="text-blue-300" />
                      <SlaBadge sla={decision.optionASla} />
                    </div>
                  )}
                  <p className="text-xs text-slate-400 leading-relaxed">{sections.optionA || decision.optionAText}</p>
                </div>

                {/* Option B */}
                <div className={`bg-emerald-950/40 rounded-xl p-3.5 flex flex-col gap-2 border ${
                  decision.optionBSla?.breachCount > 0 ? 'border-red-700/50' : 'border-emerald-800/40'
                }`}>
                  <div className="flex items-center gap-1.5">
                    <span className="text-emerald-400 font-black text-xs">💰 B</span>
                    <span className="text-xs font-bold text-emerald-300">Cost Optimal</span>
                  </div>
                  {decision.optionBMetrics && (
                    <div className="border-t border-emerald-800/30 pt-2">
                      <MetricBadge label="Makespan" value={`${decision.optionBMetrics.makespan}m`} color="text-emerald-300" />
                      <MetricBadge label="Disrupted" value={`${decision.optionBMetrics.disruptedCount} machines`} color="text-emerald-300" />
                      <SlaBadge sla={decision.optionBSla} />
                    </div>
                  )}
                  <p className="text-xs text-slate-400 leading-relaxed">{sections.optionB || decision.optionBText}</p>
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

              {/* ── Chat with Claude ── */}
              <div className="border-t border-slate-700/60 pt-4">
                <p className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-3">
                  💬 Ask Claude
                </p>

                {/* Message thread */}
                {chatMessages.length > 0 && (
                  <div className="space-y-2.5 mb-3 max-h-72 overflow-y-auto pr-1">
                    {chatMessages.map((m, i) => (
                      <ChatBubble key={i} role={m.role} content={m.content} />
                    ))}
                    {chatLoading && (
                      <div className="flex justify-start">
                        <div className="bg-slate-700/80 border border-slate-600/60 rounded-2xl rounded-bl-sm px-3.5 py-2.5 flex items-center gap-2">
                          <Spinner small />
                          <span className="text-xs text-slate-400">Claude is thinking…</span>
                        </div>
                      </div>
                    )}
                    <div ref={chatEndRef} />
                  </div>
                )}

                {/* Input row */}
                <div className="flex gap-2 items-end">
                  <textarea
                    ref={inputRef}
                    value={chatInput}
                    onChange={e => setChatInput(e.target.value)}
                    onKeyDown={handleKeyDown}
                    disabled={chatLoading}
                    placeholder="Why Option A? Is Option B safer for M3?…"
                    rows={2}
                    className="flex-1 resize-none bg-slate-800 border border-slate-700 rounded-xl px-3 py-2
                               text-xs text-slate-200 placeholder-slate-600
                               focus:outline-none focus:border-purple-500/60
                               disabled:opacity-50 transition-colors"
                  />
                  <button
                    onClick={sendChat}
                    disabled={chatLoading || !chatInput.trim()}
                    className="flex-shrink-0 h-[52px] px-3.5 bg-purple-600 hover:bg-purple-500
                               disabled:opacity-40 disabled:cursor-not-allowed
                               text-white rounded-xl transition-all duration-150 flex items-center justify-center">
                    {chatLoading
                      ? <Spinner small />
                      : <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" />
                        </svg>
                    }
                  </button>
                </div>
                <p className="text-[10px] text-slate-600 mt-1.5">Enter to send · Shift+Enter for newline</p>
              </div>

            </div>
          </div>
        </div>
      )}
    </aside>
  )
}
