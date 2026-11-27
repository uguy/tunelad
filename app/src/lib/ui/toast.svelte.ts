export interface ToastMessage {
	id: number;
	text: string;
	kind: 'info' | 'error';
}

/** How long a message stays (milliseconds). Errors stay longer: they are read, not glanced at. */
const DURATION = { info: 3500, error: 6000 };

/** The short messages that tell the user what just happened, or that something they did did not work. */
export class ToastState {
	messages = $state.raw<ToastMessage[]>([]);
	#next = 0;

	show(text: string, kind: ToastMessage['kind'] = 'info') {
		const id = this.#next++;
		this.messages = [...this.messages, { id, text, kind }];
		setTimeout(() => this.dismiss(id), DURATION[kind]);
	}

	error(text: string) {
		this.show(text, 'error');
	}

	dismiss(id: number) {
		this.messages = this.messages.filter((m) => m.id !== id);
	}
}

export const toast = new ToastState();
