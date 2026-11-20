/**
 * A CSS gradient standing in for a track's artwork: two hues taken from a hash of the track's id, so a track always
 * gets the same one, and neighbours most likely different ones.
 */
export function artworkGradient(trackId: string): string {
	let hash = 2166136261;
	for (let i = 0; i < trackId.length; i++) {
		hash ^= trackId.charCodeAt(i);
		hash = Math.imul(hash, 16777619);
	}
	const hue = Math.abs(hash) % 360;
	const second = (hue + 40 + (Math.abs(hash >> 9) % 80)) % 360;
	return `linear-gradient(135deg, hsl(${hue} 70% 55%), hsl(${second} 75% 35%))`;
}

/** Seconds as `m:ss` (or `h:mm:ss`); `0:00` for anything that is not a finite, non-negative number. */
export function formatTime(seconds: number): string {
	if (!Number.isFinite(seconds) || seconds < 0) return '0:00';
	const whole = Math.floor(seconds);
	const h = Math.floor(whole / 3600);
	const m = Math.floor((whole % 3600) / 60);
	const s = String(whole % 60).padStart(2, '0');
	return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`;
}
