<script lang="ts">
	interface Props {
		/** The waveform's peaks, from 0 to 1; null or empty draws a flat placeholder. */
		peaks: number[] | null;
		/** How much of the track has played, from 0 to 1: that part is drawn in the accent colour. */
		progress?: number;
		/** What the waveform is of, for its accessible name. */
		label: string;
		/** The user picked a point of the track, as a share of its duration from 0 to 1. */
		onseek?: (fraction: number) => void;
	}

	let { peaks, progress = 0, label, onseek }: Props = $props();

	const uid = $props.id();
	const WIDTH = 200;
	/** The baseline: bars rise above it, and their reflection hangs below. */
	const BASE = 44;
	const HEIGHT = 60;

	const ready = $derived(peaks !== null && peaks.length > 0);
	const bars = $derived(
		(peaks ?? []).map((peak, i, all) => {
			const step = WIDTH / all.length;
			const p = Math.max(0, Math.min(1, peak));
			return { x: i * step, width: step * 0.7, up: Math.max(1, p * (BASE - 2)), down: p * 14 };
		})
	);
	const clamped = $derived(Math.max(0, Math.min(1, progress)));

	function seekAt(event: MouseEvent) {
		const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
		if (box.width > 0) onseek?.(Math.max(0, Math.min(1, (event.clientX - box.left) / box.width)));
	}

	function seekByKey(event: KeyboardEvent) {
		const moves: Record<string, number> = {
			ArrowRight: clamped + 0.05,
			ArrowUp: clamped + 0.05,
			ArrowLeft: clamped - 0.05,
			ArrowDown: clamped - 0.05,
			Home: 0,
			End: 1
		};
		if (event.key in moves) {
			event.preventDefault();
			onseek?.(Math.max(0, Math.min(1, moves[event.key])));
		}
	}
</script>

{#snippet shape()}
	{#if ready}
		{#each bars as bar, i (i)}
			<rect x={bar.x} y={BASE - bar.up} width={bar.width} height={bar.up} />
			<rect
				x={bar.x}
				y={BASE + 1}
				width={bar.width}
				height={bar.down}
				class="reflection"
				opacity="0.35"
			/>
		{/each}
	{:else}
		<rect x="0" y={BASE - 1} width={WIDTH} height="2" />
	{/if}
{/snippet}

<div
	role="slider"
	tabindex="0"
	aria-label="seek {label}"
	aria-valuemin={0}
	aria-valuemax={100}
	aria-valuenow={Math.round(clamped * 100)}
	aria-valuetext="{Math.round(clamped * 100)}%"
	data-testid={ready ? 'waveform' : 'waveform-placeholder'}
	class="h-14 w-full cursor-pointer rounded-sm focus-visible:outline-2 focus-visible:outline-accent"
	onclick={seekAt}
	onkeydown={seekByKey}
>
	<svg
		viewBox="0 0 {WIDTH} {HEIGHT}"
		preserveAspectRatio="none"
		class="h-full w-full"
		aria-hidden="true"
	>
		<defs>
			<clipPath id="{uid}-played">
				<rect x="0" y="0" width={clamped * WIDTH} height={HEIGHT} data-testid="played-clip" />
			</clipPath>
		</defs>
		<g class="fill-wave">{@render shape()}</g>
		<g class="fill-accent" clip-path="url(#{uid}-played)">{@render shape()}</g>
	</svg>
</div>
