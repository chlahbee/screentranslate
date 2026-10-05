import React, { useState } from 'react';
import {
  Smartphone,
  ShieldCheck,
  Layers,
  Terminal,
  Play,
  FileCode,
  Copy,
  CheckCircle2,
  AlertCircle,
  Cpu,
  Eye,
  Sliders,
  Sparkles,
  ArrowRight,
  RefreshCw
} from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState<'overview' | 'simulator' | 'files' | 'commands'>('overview');
  const [copiedText, setCopiedText] = useState<string | null>(null);

  // Simulator states
  const [hasOverlay, setHasOverlay] = useState(false);
  const [hasNotification, setHasNotification] = useState(false);
  const [serviceRunning, setServiceRunning] = useState(false);
  const [buttonX, setButtonX] = useState(280);
  const [buttonY, setButtonY] = useState(320);
  const [isDragging, setIsDragging] = useState(false);
  const [logs, setLogs] = useState<string[]>([
    '[SYSTEM] Ready. Step 1 project initialized.'
  ]);

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedText(id);
    setTimeout(() => setCopiedText(null), 2000);
  };

  const addLog = (msg: string) => {
    setLogs((prev) => [...prev.slice(-30), `[${new Date().toLocaleTimeString()}] ${msg}`]);
  };

  const handleStartService = () => {
    setServiceRunning(true);
    addLog('ST: Screen capture permission granted, starting OverlayService');
    addLog('ST: OverlayService onCreate');
    addLog('ST: MediaProjection acquired successfully');
    addLog('ST: Floating button added to WindowManager');
  };

  const handleStopService = () => {
    setServiceRunning(false);
    addLog('ST: Received ACTION_STOP, stopping OverlayService');
    addLog('ST: Floating button removed from WindowManager');
    addLog('ST: OverlayService onDestroy');
  };

  const handleButtonTap = () => {
    addLog('ST: Floating button tapped: ready for Step 2 screen capture');
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Top Navigation */}
      <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center shadow-lg shadow-blue-600/30 text-white font-bold text-xl">
              ST
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-lg font-bold text-white tracking-tight">ScreenTranslate</h1>
                <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                  Step 1 / 6 Active
                </span>
              </div>
              <p className="text-xs text-slate-400">On-Device Screen Translation (Samsung Galaxy A52 • Android 11–14)</p>
            </div>
          </div>

          <nav className="flex items-center gap-2">
            {[
              { id: 'overview', label: 'Step 1 Status', icon: Layers },
              { id: 'simulator', label: 'Device Simulator', icon: Smartphone },
              { id: 'files', label: 'Project Files', icon: FileCode },
              { id: 'commands', label: 'Build & Run Guide', icon: Terminal },
            ].map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id as any)}
                  className={`px-3 py-2 rounded-lg text-sm font-medium transition flex items-center gap-2 ${
                    activeTab === tab.id
                      ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  {tab.label}
                </button>
              );
            })}
          </nav>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 md:p-6">
        {/* TAB 1: OVERVIEW */}
        {activeTab === 'overview' && (
          <div className="space-y-6">
            {/* Step Banner */}
            <div className="bg-gradient-to-r from-blue-900/50 via-indigo-900/30 to-slate-900 border border-blue-500/30 rounded-2xl p-6 relative overflow-hidden">
              <div className="max-w-3xl">
                <span className="text-xs font-semibold uppercase tracking-wider text-blue-400">Step 1 of 6 Ready</span>
                <h2 className="text-2xl font-bold text-white mt-1">Project Setup, Permissions & Floating Button</h2>
                <p className="text-slate-300 text-sm mt-2 leading-relaxed">
                  All Android project components have been generated with Kotlin Gradle DSL, Version Catalog, Jetpack Compose onboarding,
                  MediaProjection-compliant Foreground Service (<code className="text-blue-300 font-mono">mediaProjection</code> type),
                  and draggable edge-snapping floating button overlay.
                </p>
                <div className="mt-4 flex flex-wrap gap-3">
                  <div className="flex items-center gap-2 text-xs bg-slate-800/80 px-3 py-1.5 rounded-lg border border-slate-700 text-slate-300">
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" /> SYSTEM_ALERT_WINDOW (Overlay)
                  </div>
                  <div className="flex items-center gap-2 text-xs bg-slate-800/80 px-3 py-1.5 rounded-lg border border-slate-700 text-slate-300">
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" /> FOREGROUND_SERVICE_MEDIA_PROJECTION
                  </div>
                  <div className="flex items-center gap-2 text-xs bg-slate-800/80 px-3 py-1.5 rounded-lg border border-slate-700 text-slate-300">
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" /> POST_NOTIFICATIONS (Android 13+)
                  </div>
                  <div className="flex items-center gap-2 text-xs bg-slate-800/80 px-3 py-1.5 rounded-lg border border-slate-700 text-slate-300">
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" /> Custom View (No Compose in Overlay)
                  </div>
                </div>
              </div>
            </div>

            {/* Grid of Key Features */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
              <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 hover:border-slate-700 transition">
                <div className="w-10 h-10 rounded-lg bg-blue-500/10 text-blue-400 flex items-center justify-center mb-4">
                  <ShieldCheck className="w-5 h-5" />
                </div>
                <h3 className="font-semibold text-white">Android 14+ Compliant</h3>
                <p className="text-xs text-slate-400 mt-2 leading-relaxed">
                  Calls <code className="text-blue-300">startForeground()</code> with <code className="text-blue-300">FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION</code> before obtaining the MediaProjection token, and registers a <code className="text-blue-300">MediaProjection.Callback</code>.
                </p>
              </div>

              <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 hover:border-slate-700 transition">
                <div className="w-10 h-10 rounded-lg bg-indigo-500/10 text-indigo-400 flex items-center justify-center mb-4">
                  <Sliders className="w-5 h-5" />
                </div>
                <h3 className="font-semibold text-white">Draggable Floating Button</h3>
                <p className="text-xs text-slate-400 mt-2 leading-relaxed">
                  Compact 48dp semi-transparent floating button using Android's native <code className="text-indigo-300">WindowManager</code> with smooth magnetic edge-snapping and tap vs drag threshold recognition.
                </p>
              </div>

              <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 hover:border-slate-700 transition">
                <div className="w-10 h-10 rounded-lg bg-amber-500/10 text-amber-400 flex items-center justify-center mb-4">
                  <Cpu className="w-5 h-5" />
                </div>
                <h3 className="font-semibold text-white">Samsung One UI Optimized</h3>
                <p className="text-xs text-slate-400 mt-2 leading-relaxed">
                  Tailored for Samsung Galaxy A52 (One UI), with in-app guidance to set battery management to "Unrestricted" so the foreground service won't be killed in the background.
                </p>
              </div>
            </div>

            {/* Step Progress Checklist */}
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
              <h3 className="text-base font-bold text-white mb-4 flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-blue-400" />
                Implementation Roadmap (6 Steps)
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <div className="border border-blue-500/40 bg-blue-500/10 rounded-xl p-4">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-blue-400 uppercase">Step 1 • Current</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-blue-500/20 text-blue-300 font-semibold">Done</span>
                  </div>
                  <h4 className="font-semibold text-white text-sm">Project & Floating Button</h4>
                  <p className="text-xs text-slate-300 mt-1">Manifest permissions, Compose onboarding, foreground service, draggable button.</p>
                </div>

                <div className="border border-slate-800 bg-slate-950/60 rounded-xl p-4 opacity-75">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-slate-500 uppercase">Step 2 • Next</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-semibold">Pending</span>
                  </div>
                  <h4 className="font-semibold text-slate-200 text-sm">Background Screen Capture</h4>
                  <p className="text-xs text-slate-400 mt-1">VirtualDisplay + ImageReader, stride padding crop, clean screenshot on tap.</p>
                </div>

                <div className="border border-slate-800 bg-slate-950/60 rounded-xl p-4 opacity-75">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-slate-500 uppercase">Step 3</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-semibold">Pending</span>
                  </div>
                  <h4 className="font-semibold text-slate-200 text-sm">OCR + Text Blocks</h4>
                  <p className="text-xs text-slate-400 mt-1">ML Kit Latin recognition, bounding box extraction, English block filtering.</p>
                </div>

                <div className="border border-slate-800 bg-slate-950/60 rounded-xl p-4 opacity-75">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-slate-500 uppercase">Step 4</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-semibold">Pending</span>
                  </div>
                  <h4 className="font-semibold text-slate-200 text-sm">On-Device Translation</h4>
                  <p className="text-xs text-slate-400 mt-1">ML Kit English → Arabic translation, offline model download & caching.</p>
                </div>

                <div className="border border-slate-800 bg-slate-950/60 rounded-xl p-4 opacity-75">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-slate-500 uppercase">Step 5</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-semibold">Pending</span>
                  </div>
                  <h4 className="font-semibold text-slate-200 text-sm">In-Place Screen Overlay</h4>
                  <p className="text-xs text-slate-400 mt-1">StaticLayout RTL Arabic text drawn directly over English text boxes.</p>
                </div>

                <div className="border border-slate-800 bg-slate-950/60 rounded-xl p-4 opacity-75">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-slate-500 uppercase">Step 6</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-semibold">Pending</span>
                  </div>
                  <h4 className="font-semibold text-slate-200 text-sm">Rotation & Polish</h4>
                  <p className="text-xs text-slate-400 mt-1">Orientation handling, auto-dismiss, translating indicator, error toasts.</p>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: DEVICE SIMULATOR */}
        {activeTab === 'simulator' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Phone Frame */}
            <div className="lg:col-span-6 flex justify-center">
              <div className="w-[340px] h-[680px] bg-slate-900 border-4 border-slate-700 rounded-[44px] shadow-2xl relative overflow-hidden flex flex-col select-none">
                {/* Samsung Camera Notch & Speaker */}
                <div className="h-6 w-full flex items-center justify-center pt-2">
                  <div className="w-3.5 h-3.5 bg-black rounded-full border border-slate-800"></div>
                </div>

                {/* Status Bar */}
                <div className="px-6 py-1 flex items-center justify-between text-[11px] text-slate-400 font-mono">
                  <span>10:45</span>
                  <div className="flex items-center gap-1.5">
                    <span>5G</span>
                    <span>100%</span>
                  </div>
                </div>

                {/* Simulated Screen Content */}
                <div className="flex-1 overflow-y-auto px-4 py-2 relative">
                  {!serviceRunning ? (
                    // Compose Onboarding UI
                    <div className="space-y-4 pt-2">
                      <div className="text-center">
                        <div className="w-14 h-14 bg-blue-600 rounded-full mx-auto flex items-center justify-center text-white text-xl font-bold shadow-lg shadow-blue-600/40">
                          文A
                        </div>
                        <h3 className="text-lg font-bold text-white mt-2">ScreenTranslate</h3>
                        <p className="text-xs text-slate-400">English → Arabic on-device screen translation</p>
                      </div>

                      {/* Permission Card 1 */}
                      <div className={`p-3 rounded-xl border ${hasOverlay ? 'bg-emerald-950/40 border-emerald-500/40' : 'bg-slate-800 border-slate-700'}`}>
                        <div className="flex justify-between items-center text-xs font-semibold">
                          <span className="text-white">1. Display Over Other Apps</span>
                          <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${hasOverlay ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'}`}>
                            {hasOverlay ? 'GRANTED' : 'REQUIRED'}
                          </span>
                        </div>
                        <p className="text-[11px] text-slate-400 mt-1">Allows the floating button to float above your apps.</p>
                        {!hasOverlay && (
                          <button
                            onClick={() => {
                              setHasOverlay(true);
                              addLog('Overlay permission granted in Settings');
                            }}
                            className="mt-2 text-xs font-medium text-blue-400 hover:text-blue-300 underline"
                          >
                            Grant Overlay Permission
                          </button>
                        )}
                      </div>

                      {/* Permission Card 2 */}
                      <div className={`p-3 rounded-xl border ${hasNotification ? 'bg-emerald-950/40 border-emerald-500/40' : 'bg-slate-800 border-slate-700'}`}>
                        <div className="flex justify-between items-center text-xs font-semibold">
                          <span className="text-white">2. Notifications</span>
                          <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${hasNotification ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'}`}>
                            {hasNotification ? 'GRANTED' : 'REQUIRED'}
                          </span>
                        </div>
                        <p className="text-[11px] text-slate-400 mt-1">Required for MediaProjection foreground service.</p>
                        {!hasNotification && (
                          <button
                            onClick={() => {
                              setHasNotification(true);
                              addLog('POST_NOTIFICATIONS permission granted');
                            }}
                            className="mt-2 text-xs font-medium text-blue-400 hover:text-blue-300 underline"
                          >
                            Grant Notification Permission
                          </button>
                        )}
                      </div>

                      {/* Battery Card */}
                      <div className="bg-slate-800/80 border border-slate-700/80 p-3 rounded-xl">
                        <span className="text-xs font-semibold text-sky-400">💡 Galaxy A52 (One UI) Tip</span>
                        <p className="text-[11px] text-slate-300 mt-1 leading-snug">
                          Set App Battery Usage to "Unrestricted" in Settings so Samsung doesn't terminate background translation.
                        </p>
                      </div>

                      {/* Start Button */}
                      <button
                        disabled={!hasOverlay || !hasNotification}
                        onClick={handleStartService}
                        className={`w-full py-3 rounded-xl text-xs font-bold transition flex items-center justify-center gap-2 ${
                          hasOverlay && hasNotification
                            ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/30 hover:bg-blue-500'
                            : 'bg-slate-800 text-slate-500 cursor-not-allowed'
                        }`}
                      >
                        <Play className="w-3.5 h-3.5 fill-current" />
                        Start ScreenTranslate
                      </button>
                    </div>
                  ) : (
                    // Over-App Simulation (e.g. reading an English news or message)
                    <div className="space-y-3 pt-2">
                      <div className="p-3 bg-slate-800/90 rounded-xl border border-slate-700 text-xs">
                        <div className="flex items-center justify-between text-slate-400 mb-1">
                          <span className="font-semibold text-blue-400">WhatsApp / Web Article</span>
                          <span>Now</span>
                        </div>
                        <p className="text-slate-200 leading-relaxed font-sans">
                          "Artificial intelligence on mobile devices enables instant translation without sending personal data to remote servers."
                        </p>
                      </div>

                      <div className="p-3 bg-slate-800/90 rounded-xl border border-slate-700 text-xs">
                        <p className="text-slate-200 leading-relaxed font-sans">
                          "Tap the floating translation button to test the drag, snapping, and tap detection."
                        </p>
                      </div>

                      <div className="p-3 bg-slate-800/90 rounded-xl border border-slate-700 text-xs text-slate-400">
                        <span className="text-amber-400 font-semibold block mb-1">Ongoing Notification</span>
                        ScreenTranslate is active • Tap floating button to translate
                        <button
                          onClick={handleStopService}
                          className="mt-2 block w-full py-1 bg-rose-600/20 text-rose-400 border border-rose-500/30 rounded text-center text-[10px] font-bold"
                        >
                          STOP SERVICE
                        </button>
                      </div>
                    </div>
                  )}

                  {/* The Floating Button (Shown when service running) */}
                  {serviceRunning && (
                    <div
                      style={{
                        position: 'absolute',
                        left: `${buttonX}px`,
                        top: `${buttonY}px`,
                        cursor: 'grab',
                      }}
                      onMouseDown={(e) => {
                        setIsDragging(true);
                      }}
                      onClick={handleButtonTap}
                      title="Tap to trigger translation (or drag to move)"
                      className="w-12 h-12 rounded-full bg-indigo-900/90 border-2 border-white/80 shadow-2xl flex items-center justify-center text-white font-bold text-xs select-none hover:scale-105 transition-transform active:cursor-grabbing z-50 group"
                    >
                      <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center shadow-inner">
                        文A
                      </div>
                    </div>
                  )}
                </div>

                {/* Home Indicator Bar */}
                <div className="h-5 w-full flex items-center justify-center pb-1">
                  <div className="w-28 h-1 bg-slate-600 rounded-full"></div>
                </div>
              </div>
            </div>

            {/* Live Logcat & Controls */}
            <div className="lg:col-span-6 space-y-4">
              <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  <Terminal className="w-4 h-4 text-emerald-400" />
                  Live Android Logcat (<code className="text-emerald-300">tag: ST</code>)
                </h3>
                <p className="text-xs text-slate-400 mt-1">
                  All service lifecycle events, permissions, and floating button touches log with tag <code className="text-blue-300">ST</code>.
                </p>

                <div className="bg-black/80 rounded-lg p-3 font-mono text-xs text-emerald-400 h-64 overflow-y-auto mt-3 space-y-1 border border-slate-800">
                  {logs.map((log, index) => (
                    <div key={index} className="leading-tight">
                      {log}
                    </div>
                  ))}
                </div>

                <div className="mt-3 flex gap-2">
                  <button
                    onClick={() => setLogs(['[SYSTEM] Log cleared.'])}
                    className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs text-slate-300 font-medium transition"
                  >
                    Clear Logs
                  </button>
                  {serviceRunning ? (
                    <button
                      onClick={handleStopService}
                      className="px-3 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-xs text-white font-medium transition"
                    >
                      Simulate Notification Stop
                    </button>
                  ) : (
                    <button
                      onClick={() => {
                        setHasOverlay(true);
                        setHasNotification(true);
                        handleStartService();
                      }}
                      className="px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-xs text-white font-medium transition"
                    >
                      Quick Launch Service
                    </button>
                  )}
                </div>
              </div>

              <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
                <h3 className="text-sm font-bold text-white">How it works under the hood</h3>
                <ul className="text-xs text-slate-400 space-y-2 mt-2">
                  <li className="flex items-start gap-2">
                    <span className="text-blue-400 font-bold">•</span>
                    <span><strong>No ComposeView in WindowManager:</strong> Uses custom Android <code className="text-slate-300">FloatingButtonView</code> extending <code className="text-slate-300">View</code> to eliminate overlay lifecycle leaks.</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <span className="text-blue-400 font-bold">•</span>
                    <span><strong>Android 14 Foreground Token:</strong> <code className="text-slate-300">startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)</code> is called strictly before <code className="text-slate-300">getMediaProjection()</code>.</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <span className="text-blue-400 font-bold">•</span>
                    <span><strong>Edge Snapping:</strong> On finger release, <code className="text-slate-300">ValueAnimator</code> smoothly shifts <code className="text-slate-300">layoutParams.x</code> to the nearest screen border.</span>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: PROJECT FILES */}
        {activeTab === 'files' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-white">Complete Android Project Files (Step 1)</h3>
                <p className="text-xs text-slate-400">All files are written in full with 0 placeholders, ready for Gradle.</p>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <FileCard
                path="gradle/libs.versions.toml"
                description="Gradle Version Catalog with AGP 8.3.2, Kotlin 1.9.24, Compose BOM, Coroutines, and ML Kit dependencies."
                copyAction={() => copyToClipboard('gradle/libs.versions.toml', 'toml')}
                copied={copiedText === 'toml'}
              />
              <FileCard
                path="settings.gradle.kts & build.gradle.kts"
                description="Root Kotlin DSL build scripts with Google & MavenCentral repositories and plugin definitions."
                copyAction={() => copyToClipboard('settings.gradle.kts', 'settings')}
                copied={copiedText === 'settings'}
              />
              <FileCard
                path="app/build.gradle.kts"
                description="App module build script with minSdk 26, targetSdk 34, Compose enabled, and full dependency declarations."
                copyAction={() => copyToClipboard('app/build.gradle.kts', 'appbuild')}
                copied={copiedText === 'appbuild'}
              />
              <FileCard
                path="app/src/main/AndroidManifest.xml"
                description="Declares SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE_MEDIA_PROJECTION, POST_NOTIFICATIONS, and OverlayService."
                copyAction={() => copyToClipboard('app/src/main/AndroidManifest.xml', 'manifest')}
                copied={copiedText === 'manifest'}
              />
              <FileCard
                path="app/src/main/java/.../MainActivity.kt"
                description="Jetpack Compose onboarding UI checking overlay and notification permissions, with MediaProjection launcher."
                copyAction={() => copyToClipboard('MainActivity.kt', 'mainact')}
                copied={copiedText === 'mainact'}
              />
              <FileCard
                path="app/src/main/java/.../OverlayService.kt"
                description="Foreground Service with mediaProjection type, notification with Stop action, and floating button controller."
                copyAction={() => copyToClipboard('OverlayService.kt', 'service')}
                copied={copiedText === 'service'}
              />
              <FileCard
                path="app/src/main/java/.../FloatingButtonView.kt"
                description="Native Android View (no Compose in overlay) drawing circular badge, handling drag, edge-snapping, and tap."
                copyAction={() => copyToClipboard('FloatingButtonView.kt', 'floatview')}
                copied={copiedText === 'floatview'}
              />
              <FileCard
                path="app/src/main/java/.../Constants.kt"
                description="Centralized constants: TAG='ST', SOURCE_LANG='en', TARGET_LANG='ar', notification IDs, and actions."
                copyAction={() => copyToClipboard('Constants.kt', 'consts')}
                copied={copiedText === 'consts'}
              />
            </div>
          </div>
        )}

        {/* TAB 4: COMMANDS */}
        {activeTab === 'commands' && (
          <div className="max-w-4xl space-y-6">
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
              <h3 className="text-base font-bold text-white mb-2">Build & Install Commands (Fedora Linux)</h3>
              <p className="text-xs text-slate-400 mb-6 leading-relaxed">
                Connect your Samsung Galaxy A52 via USB with <strong>USB Debugging enabled</strong> in Developer Options. Run these commands directly in your terminal:
              </p>

              <div className="space-y-4">
                <div>
                  <div className="flex justify-between items-center text-xs font-semibold text-slate-300 mb-1.5">
                    <span>1. Assemble Debug APK</span>
                    <button
                      onClick={() => copyToClipboard('./gradlew assembleDebug', 'cmd1')}
                      className="text-blue-400 hover:text-blue-300 flex items-center gap-1 text-[11px]"
                    >
                      {copiedText === 'cmd1' ? <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                      {copiedText === 'cmd1' ? 'Copied' : 'Copy'}
                    </button>
                  </div>
                  <pre className="bg-black/90 text-emerald-400 p-3 rounded-lg font-mono text-xs overflow-x-auto border border-slate-800">
                    ./gradlew assembleDebug
                  </pre>
                </div>

                <div>
                  <div className="flex justify-between items-center text-xs font-semibold text-slate-300 mb-1.5">
                    <span>2. Install on Samsung Galaxy A52 via ADB</span>
                    <button
                      onClick={() => copyToClipboard('adb install -r app/build/outputs/apk/debug/app-debug.apk', 'cmd2')}
                      className="text-blue-400 hover:text-blue-300 flex items-center gap-1 text-[11px]"
                    >
                      {copiedText === 'cmd2' ? <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                      {copiedText === 'cmd2' ? 'Copied' : 'Copy'}
                    </button>
                  </div>
                  <pre className="bg-black/90 text-emerald-400 p-3 rounded-lg font-mono text-xs overflow-x-auto border border-slate-800">
                    adb install -r app/build/outputs/apk/debug/app-debug.apk
                  </pre>
                </div>

                <div>
                  <div className="flex justify-between items-center text-xs font-semibold text-slate-300 mb-1.5">
                    <span>3. View ScreenTranslate Logcat Stream</span>
                    <button
                      onClick={() => copyToClipboard('adb logcat -s ST:D', 'cmd3')}
                      className="text-blue-400 hover:text-blue-300 flex items-center gap-1 text-[11px]"
                    >
                      {copiedText === 'cmd3' ? <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                      {copiedText === 'cmd3' ? 'Copied' : 'Copy'}
                    </button>
                  </div>
                  <pre className="bg-black/90 text-emerald-400 p-3 rounded-lg font-mono text-xs overflow-x-auto border border-slate-800">
                    adb logcat -s ST:D
                  </pre>
                </div>
              </div>
            </div>

            <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
              <h4 className="text-sm font-bold text-white mb-2">What you should see on your phone:</h4>
              <ol className="list-decimal list-inside text-xs text-slate-300 space-y-2 leading-relaxed">
                <li>Launch <strong>ScreenTranslate</strong> on the phone.</li>
                <li>Tap <strong>"Grant Overlay Permission"</strong> → enables "Display over other apps" for ScreenTranslate in Android Settings.</li>
                <li>Tap <strong>"Grant Notification Permission"</strong> (if prompted on Android 13/14).</li>
                <li>Tap <strong>"Start ScreenTranslate"</strong> → accept the Android screen capture consent dialog ("Start now").</li>
                <li>The app minimizes to the background, and a small ~48dp semi-transparent blue button appears floating over your phone screen!</li>
                <li>Drag the floating button around the screen — when you let go, it smoothly snaps to the nearest screen edge.</li>
                <li>Pull down the notification shade: notice the <strong>"ScreenTranslate is active"</strong> notification with a <strong>"Stop"</strong> button. Tapping "Stop" removes the floating button and cleans up the service.</li>
              </ol>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

function FileCard({ path, description, copyAction, copied }: { path: string; description: string; copyAction: () => void; copied: boolean }) {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 flex flex-col justify-between hover:border-slate-700 transition">
      <div>
        <div className="flex items-center justify-between">
          <code className="text-xs font-mono font-semibold text-blue-400">{path}</code>
          <button
            onClick={copyAction}
            className="text-slate-400 hover:text-white p-1 rounded"
            title="Copy path"
          >
            {copied ? <CheckCircle2 className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
          </button>
        </div>
        <p className="text-xs text-slate-400 mt-2 leading-relaxed">{description}</p>
      </div>
      <div className="mt-3 pt-3 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-500">
        <span>Step 1 Complete</span>
        <span className="text-emerald-400 font-semibold">Ready</span>
      </div>
    </div>
  );
}
