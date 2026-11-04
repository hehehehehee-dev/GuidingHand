package vn.guidinghand

/** Android could not produce a frame although the safety-checked screen itself stayed current. */
class SnapshotCaptureUnavailable : RuntimeException()

/** Android refused a protected app window. Never substitute a black display capture. */
class SecureWindowCaptureUnavailable : RuntimeException()
