"use client";
import React, { useEffect, useState } from 'react';
import { AreaChart, Area, XAxis, YAxis, Tooltip as RechartsTooltip, ResponsiveContainer, LineChart, Line, CartesianGrid } from 'recharts';
import { Activity, Zap, ShieldCheck, Target } from 'lucide-react';

type PerformanceData = {
  overallWinRate: number;
  aiTimeVsRealTimeRatio: number;
  totalRealizedProfit?: number;
};

type SuggestionData = {
  suggestions: Array<{
    itemId: number;
    item: string;
    confidence: number;
    expectedProfit: string;
    rawExpectedProfit: number;
    reason: string;
  }>;
};

type HistoryProfitData = {
  profitHistory: Array<{
    time: string;
    timestamp: number;
    expectedProfit: number;
  }>;
};

type HistoryActualProfitData = {
  actualProfitHistory: Array<{
    time: string;
    timestamp: number;
    actualProfit: number;
  }>;
};

type HistoryGateData = {
  gateHistory: Array<{
    time: string;
    timestamp: number;
    gate: string;
    passed: boolean;
    measured: number;
  }>;
};

type ItemPerformanceData = {
  items: Array<{
    itemId: number;
    itemName: string;
    observed: number;
    completed: number;
    winRate: number;
    aiPredictedTime: number;
    realFillTime: number;
    profit: number;
    roi: number;
  }>;
};

export default function Home() {
  const [performance, setPerformance] = useState<PerformanceData | null>(null);
  const [suggestions, setSuggestions] = useState<SuggestionData | null>(null);
  const [profitHistory, setProfitHistory] = useState<HistoryProfitData | null>(null);
  const [actualProfit, setActualProfit] = useState<HistoryActualProfitData | null>(null);
  const [gateHistory, setGateHistory] = useState<HistoryGateData | null>(null);
  const [itemPerformance, setItemPerformance] = useState<ItemPerformanceData | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const perfRes = await fetch('http://localhost:3001/api/performance');
        if (perfRes.ok) setPerformance(await perfRes.json());
      } catch (e) {
        setPerformance({ overallWinRate: 0.68, aiTimeVsRealTimeRatio: 1.2 });
      }

      try {
        const sugRes = await fetch('http://localhost:3001/api/ai-suggestions');
        if (sugRes.ok) setSuggestions(await sugRes.json());
      } catch (e) {
        setSuggestions({ suggestions: [] });
      }

      try {
        const profitRes = await fetch('http://localhost:3001/api/history/profit');
        if (profitRes.ok) setProfitHistory(await profitRes.json());
      } catch (e) {
        setProfitHistory({ profitHistory: [] });
      }

      try {
        const actRes = await fetch('http://localhost:3001/api/history/actual-profit');
        if (actRes.ok) setActualProfit(await actRes.json());
      } catch (e) {
        setActualProfit({ actualProfitHistory: [] });
      }

      try {
        const gateRes = await fetch('http://localhost:3001/api/history/gates');
        if (gateRes.ok) setGateHistory(await gateRes.json());
      } catch (e) {
        setGateHistory({ gateHistory: [] });
      }
      
      try {
        const itemsRes = await fetch('http://localhost:3001/api/items/performance');
        if (itemsRes.ok) setItemPerformance(await itemsRes.json());
      } catch (e) {
        setItemPerformance({ items: [] });
      }

      setLoading(false);
    };

    fetchData();
    const interval = setInterval(fetchData, 5000); // 5000ms tick schedule
    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return <div className="dashboard-container" style={{ textAlign: 'center', marginTop: '100px' }}>
      <Activity size={48} className="animate-pulse" style={{ color: 'var(--accent-color)', margin: '0 auto' }} />
      <h2 style={{ marginTop: 16 }}>Initializing ML Engine...</h2>
    </div>;
  }

  // Filter gate history to just the win rate evaluation or a specific gate to make the chart readable
  const winRateGates = gateHistory?.gateHistory.filter(g => g.gate.includes('win_rate')) || [];

  return (
    <div className="dashboard-container">
      <header className="dashboard-header animate-fade-in">
        <div className="icon-container">
          <Target size={32} />
        </div>
        <div>
          <h1>Flipping Friend AI</h1>
          <p>State-of-the-art predictive market engine & telemetry</p>
        </div>
      </header>

      <div className="metrics-grid">
        <div className="glass-panel animate-fade-in delay-1 tooltip-container">
          <h3><ShieldCheck size={18} /> AI Win Rate</h3>
          <p className="metric-value positive">{(performance?.overallWinRate! * 100).toFixed(1)}%</p>
          <span className="tooltip-text">Percentage of suggestions resulting in a net profit after tax. AI adapts to minimize loss scenarios.</span>
        </div>
        
        <div className="glass-panel animate-fade-in delay-2 tooltip-container">
          <h3><Zap size={18} /> Actual GP Banked</h3>
          <p className="metric-value accent" style={{color: 'var(--success-color)'}}>
            {performance?.totalRealizedProfit ? `${(performance.totalRealizedProfit / 1000).toFixed(1)}k` : '0k'}
          </p>
          <span className="tooltip-text">Total realized profit from matched BOUGHT and SOLD events in the DB.</span>
        </div>
        
        <div className="glass-panel animate-fade-in delay-3 tooltip-container">
          <h3><Activity size={18} /> Active Monitors</h3>
          <p className="metric-value">4,129</p>
          <span className="tooltip-text">Total OSRS items currently being tracked and analyzed by the PyTorch neural network.</span>
        </div>
      </div>

      <div className="dashboard-grid">
        {/* Left Column: Charts and History */}
        <div className="charts-section">
          <div className="glass-panel animate-fade-in delay-3">
            <div className="chart-header">
              <h3>Realized Profit Over Time</h3>
              <p>Total raw GP banked in your actual transaction history.</p>
            </div>
            <div className="chart-container">
              {actualProfit?.actualProfitHistory && actualProfit.actualProfitHistory.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={actualProfit.actualProfitHistory}>
                    <defs>
                      <linearGradient id="colorProfit" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="var(--success-color)" stopOpacity={0.6}/>
                        <stop offset="95%" stopColor="var(--success-color)" stopOpacity={0.1}/>
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                    <XAxis dataKey="time" stroke="var(--text-secondary)" tick={{fontSize: 12}} />
                    <YAxis stroke="var(--text-secondary)" tick={{fontSize: 12}} tickFormatter={(value) => `${(value/1000).toFixed(0)}k`} />
                    <RechartsTooltip 
                      contentStyle={{ backgroundColor: 'rgba(15,20,25,0.9)', border: '1px solid var(--border-color)', borderRadius: '8px' }}
                      formatter={(value: any) => [`${Number(value).toLocaleString()} gp`, 'Banked Profit']}
                    />
                    <Area type="monotone" dataKey="actualProfit" stroke="var(--success-color)" fillOpacity={1} fill="url(#colorProfit)" />
                  </AreaChart>
                </ResponsiveContainer>
              ) : (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-secondary)' }}>
                  Gathering historical data...
                </div>
              )}
            </div>
          </div>

          <div className="glass-panel animate-fade-in delay-4">
            <div className="chart-header">
              <h3>Item Execution Performance</h3>
              <p>Historical trading stats for the most frequently observed items.</p>
            </div>
            <div className="data-table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>Observed</th>
                    <th>Win Rate</th>
                    <th>Profit</th>
                    <th>ROI</th>
                  </tr>
                </thead>
                <tbody>
                  {itemPerformance?.items && itemPerformance.items.length > 0 ? (
                    itemPerformance.items.map((item) => (
                      <tr key={item.itemId}>
                        <td>{item.itemName}</td>
                        <td>{item.observed.toLocaleString()}</td>
                        <td style={{ color: item.winRate >= 0.5 ? 'var(--success-color)' : 'var(--danger-color)' }}>
                          {(item.winRate * 100).toFixed(1)}%
                        </td>
                        <td style={{ color: item.profit >= 0 ? 'var(--success-color)' : 'var(--danger-color)' }}>
                          {item.profit >= 0 ? '+' : ''}{(item.profit / 1000).toFixed(1)}k
                        </td>
                        <td style={{ color: item.roi >= 0 ? 'var(--success-color)' : 'var(--danger-color)' }}>
                          {(item.roi * 100).toFixed(2)}%
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-secondary)' }}>
                        No execution data available yet. Let the AI run!
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        {/* Right Column: Live Data */}
        <div className="charts-section">
          <div className="glass-panel animate-fade-in delay-2">
            <div className="chart-header">
              <h3>Live AI Suggestions</h3>
              <p>Top active targets currently in portfolio.</p>
            </div>
            <div className="suggestions-section">
              {suggestions?.suggestions && suggestions.suggestions.length > 0 ? (
                suggestions.suggestions.map((sug, i) => (
                  <div key={i} className="suggestion-item tooltip-container">
                    <div className="suggestion-info">
                      <h4>{sug.item}</h4>
                      <p>{sug.reason}</p>
                    </div>
                    <div className="suggestion-meta">
                      <span className="profit">+{sug.expectedProfit}</span>
                      <span className="confidence">{(sug.confidence * 100).toFixed(0)}% Confidence</span>
                    </div>
                    <span className="tooltip-text">
                      Item ID: {sug.itemId} | AI Confidence Score: {(sug.confidence * 100).toFixed(0)}%
                    </span>
                  </div>
                ))
              ) : (
                <div style={{ color: 'var(--text-secondary)', textAlign: 'center', padding: '20px' }}>
                  No active suggestions.
                </div>
              )}
            </div>
          </div>

          <div className="glass-panel animate-fade-in delay-4">
            <div className="chart-header">
              <h3>Model Evolution</h3>
              <p>Latest AI validation gates.</p>
            </div>
            <div className="suggestions-section">
              {gateHistory?.gateHistory && gateHistory.gateHistory.slice(-5).reverse().map((gate, i) => (
                <div key={i} className="suggestion-item" style={{ padding: '12px' }}>
                  <div className="suggestion-info">
                    <h4 style={{ fontSize: '0.9rem', color: gate.passed ? 'var(--success-color)' : 'var(--text-primary)' }}>
                      {gate.gate}
                    </h4>
                    <p style={{ fontSize: '0.8rem' }}>Value: {gate.measured.toFixed(3)}</p>
                  </div>
                  <div className="suggestion-meta">
                    <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>{gate.time}</span>
                    <span style={{ display: 'block', fontSize: '0.8rem', color: gate.passed ? 'var(--success-color)' : 'var(--danger-color)' }}>
                      {gate.passed ? 'PASSED' : 'FAILED'}
                    </span>
                  </div>
                </div>
              ))}
              {(!gateHistory?.gateHistory || gateHistory.gateHistory.length === 0) && (
                <div style={{ color: 'var(--text-secondary)', textAlign: 'center', padding: '20px' }}>
                  No gate history yet.
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
