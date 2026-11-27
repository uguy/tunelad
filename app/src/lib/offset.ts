/** The largest start offset the server accepts, in seconds. */
export const MAX_OFFSET = 86_400;

/** A start offset as people read it: "1:35", or "1:02:03" from an hour on; the fraction of a second is kept. */
export function formatOffset(seconds: number): string {
	const whole = Math.floor(seconds);
	const fraction = seconds - whole;
	const hours = Math.floor(whole / 3600);
	const minutes = Math.floor((whole % 3600) / 60);
	const secs = whole % 60;
	const tail =
		fraction > 0
			? `.${String(Math.round(fraction * 1000))
					.padStart(3, '0')
					.replace(/0+$/, '')}`
			: '';
	const two = (n: number) => String(n).padStart(2, '0');
	return hours > 0
		? `${hours}:${two(minutes)}:${two(secs)}${tail}`
		: `${minutes}:${two(secs)}${tail}`;
}

/**
 * The seconds a typed offset stands for: "95.5", "1:35", "1:35.5" or "1:02:03". Empty text is `null` (no offset given),
 * and text that is not an offset, negative or over a day is `NaN`.
 */
export function parseOffset(text: string): number | null {
	const trimmed = text.trim();
	if (trimmed === '') return null;
	if (!/^\d+(:\d{1,2}){0,2}(\.\d+)?$/.test(trimmed)) return NaN;
	const parts = trimmed.split(':').map(Number);
	const seconds = parts.reduce((total, part) => total * 60 + part, 0);
	const [last] = parts.slice(-1);
	if (parts.length > 1 && last >= 60) return NaN;
	if (parts.length > 2 && parts[1] >= 60) return NaN;
	return seconds > MAX_OFFSET ? NaN : seconds;
}
