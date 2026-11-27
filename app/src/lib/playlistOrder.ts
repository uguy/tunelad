/** Where a dragged row lands, and what the order becomes: pure, so that it is tested without a browser. */

/** `items` with the one at `from` moved to index `to` of the result. */
export function moveItem<T>(items: readonly T[], from: number, to: number): T[] {
	if (from < 0 || from >= items.length || to < 0 || to >= items.length) return [...items];
	const result = [...items];
	const [moved] = result.splice(from, 1);
	result.splice(to, 0, moved);
	return result;
}

/**
 * The index a row dragged from `from` ends at when dropped on the row at `over`: `after` is whether the pointer is
 * in the lower half of that row. The result is an index of the reordered list, so that `moveItem(items, from, to)`
 * does what the drop shows.
 */
export function dropIndex(from: number, over: number, after: boolean): number {
	const slot = after ? over + 1 : over; // the gap between rows the pointer is at
	return slot > from ? slot - 1 : slot;
}

/** Whether the pointer at `clientY` is in the lower half of the row's box. */
export function inLowerHalf(clientY: number, box: { top: number; height: number }): boolean {
	return clientY > box.top + box.height / 2;
}
