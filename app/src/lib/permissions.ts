import type { Account } from '#lib/session.svelte.js';

/** The part of a track that decides who may change it. */
export interface OwnedTrack {
	owner?: { id: string } | null;
}

/** Whether the logged-in user may edit and delete the track: its owner, or any administrator. */
export function canChange(me: Account | null, track: OwnedTrack): boolean {
	return me != null && (me.admin || track.owner?.id === me.id);
}
