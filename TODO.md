# TODO: Fix Deprecation Warnings in TimerService.kt

- [x] Replace LocalBroadcastManager.getInstance(this).sendBroadcast(intent) with sendBroadcast(intent) in sendUpdateBroadcast() method
- [x] Replace LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("TIMER_STEP_FINISHED")) with sendBroadcast(Intent("TIMER_STEP_FINISHED")) in onTimerFinished() method
- [x] Replace LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("TIMER_ALL_FINISHED")) with sendBroadcast(Intent("TIMER_ALL_FINISHED")) in onTimerFinished() method
- [x] Remove ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE from startForeground calls in onStartCommand() and loadState()
- [x] Replace stopForeground(true) with stopForeground(STOP_FOREGROUND_REMOVE) in ACTION_RESET_TIMER, ACTION_STOP_TIMER, onTimerFinished(), and onDestroy()
- [x] Remove import for LocalBroadcastManager
- [x] Add import for STOP_FOREGROUND_REMOVE if needed
