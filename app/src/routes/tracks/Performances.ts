import { parseOffset } from '#lib/offset.js';

/** The longest key and notes the server accepts. */
export const MAX_KEY = 30;
export const MAX_NOTES = 2000;

/** What a Performance's fields hold while the form is open: the offset as typed. */
export interface PerformanceForm {
	start: string;
	key: string;
	notes: string;
}

/** A Performance as the server takes it. */
export interface PerformanceInput {
	startOffsetSeconds?: number;
	key?: string;
	notes?: string;
}

export const emptyPerformance = (): PerformanceForm => ({ start: '', key: '', notes: '' });

/** One empty form per tune. */
export function emptyPerformances(count: number): PerformanceForm[] {
	return Array.from({ length: count }, emptyPerformance);
}

/** Why a Performance's fields cannot be sent, or undefined when they can. */
export function checkPerformance(form: PerformanceForm): string | undefined {
	if (Number.isNaN(parseOffset(form.start)))
		return 'The start must be a time such as 1:35, up to 24 hours.';
	if (form.key.trim().length > MAX_KEY) return `A key has at most ${MAX_KEY} characters.`;
	if (form.notes.trim().length > MAX_NOTES) return `Notes have at most ${MAX_NOTES} characters.`;
	return undefined;
}

/** The server's view of the form; empty fields are left out. */
export function toInput(form: PerformanceForm): PerformanceInput {
	const start = parseOffset(form.start);
	const input: PerformanceInput = {};
	if (start !== null && !Number.isNaN(start)) input.startOffsetSeconds = start;
	if (form.key.trim()) input.key = form.key.trim();
	if (form.notes.trim()) input.notes = form.notes.trim();
	return input;
}
