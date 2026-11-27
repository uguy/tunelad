<script lang="ts">
	import Time from 'svelte-time';
	import type { PageProps } from './$types';

	// replaced dynamically
	const frontendBuildDate = __DATE__;

	let { data }: PageProps = $props();
</script>

<svelte:head>
	<title>Tune Lad - About</title>
</svelte:head>

<section class="flex flex-col gap-2">
	<h1 class="text-3xl font-bold tracking-tight">
		{data.info.app.name}
	</h1>
	<p class="text-ink-muted">
		{data.info.app.description}
	</p>
	<div class="flex flex-wrap gap-4">
		<div class="card my-5 min-w-72 flex-1 overflow-hidden">
			<div class="w-full border-b border-line bg-canvas px-6 py-4">
				<h2 class="text-xl font-semibold">
					Health
					{#if data.health.status === 'UP'}
						<svg
							xmlns="http://www.w3.org/2000/svg"
							viewBox="0 0 24 24"
							fill="currentColor"
							class="inline h-5 w-5 text-accent"
						>
							<path
								d="M12 2.25a.75.75 0 01.75.75v2.25a.75.75 0 01-1.5 0V3a.75.75 0 01.75-.75zM7.5 12a4.5 4.5 0 119 0 4.5 4.5 0 01-9 0zM18.894 6.166a.75.75 0 00-1.06-1.06l-1.591 1.59a.75.75 0 101.06 1.061l1.591-1.59zM21.75 12a.75.75 0 01-.75.75h-2.25a.75.75 0 010-1.5H21a.75.75 0 01.75.75zM17.834 18.894a.75.75 0 001.06-1.06l-1.59-1.591a.75.75 0 10-1.061 1.06l1.59 1.591zM12 18a.75.75 0 01.75.75V21a.75.75 0 01-1.5 0v-2.25A.75.75 0 0112 18zM7.758 17.303a.75.75 0 00-1.061-1.06l-1.591 1.59a.75.75 0 001.06 1.061l1.591-1.59zM6 12a.75.75 0 01-.75.75H3a.75.75 0 010-1.5h2.25A.75.75 0 016 12zM6.697 7.757a.75.75 0 001.06-1.06l-1.59-1.591a.75.75 0 00-1.061 1.06l1.59 1.591z"
							/>
						</svg>
					{:else}
						<svg
							xmlns="http://www.w3.org/2000/svg"
							viewBox="0 0 24 24"
							fill="currentColor"
							class="inline h-5 w-5 text-danger"
						>
							<path
								fill-rule="evenodd"
								d="M12 2.25c-5.385 0-9.75 4.365-9.75 9.75s4.365 9.75 9.75 9.75 9.75-4.365 9.75-9.75S17.385 2.25 12 2.25zm-2.625 6c-.54 0-.828.419-.936.634a1.96 1.96 0 00-.189.866c0 .298.059.605.189.866.108.215.395.634.936.634.54 0 .828-.419.936-.634.13-.26.189-.568.189-.866 0-.298-.059-.605-.189-.866-.108-.215-.395-.634-.936-.634zm4.314.634c.108-.215.395-.634.936-.634.54 0 .828.419.936.634.13.26.189.568.189.866 0 .298-.059.605-.189.866-.108.215-.395.634-.936.634-.54 0-.828-.419-.936-.634a1.96 1.96 0 01-.189-.866c0-.298.059-.605.189-.866zm-4.34 7.964a.75.75 0 01-1.061-1.06 5.236 5.236 0 013.73-1.538 5.236 5.236 0 013.695 1.538.75.75 0 11-1.061 1.06 3.736 3.736 0 00-2.639-1.098 3.736 3.736 0 00-2.664 1.098z"
								clip-rule="evenodd"
							/>
						</svg>
					{/if}
				</h2>
			</div>
			<div class="stat-panel p-6">
				<h2 class="mt-4 text-base font-semibold first:mt-0">
					Database
					{#if data.health.components.r2dbc.status === 'UP'}
						<svg
							xmlns="http://www.w3.org/2000/svg"
							fill="none"
							viewBox="0 0 24 24"
							stroke-width="1.5"
							stroke="currentColor"
							class="w-6 h-6 text-success inline"
						>
							<path
								stroke-linecap="round"
								stroke-linejoin="round"
								d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
							/>
						</svg>
					{:else}
						<svg
							xmlns="http://www.w3.org/2000/svg"
							fill="none"
							viewBox="0 0 24 24"
							stroke-width="1.5"
							stroke="currentColor"
							class="w-6 h-6 text-danger inline"
						>
							<path
								stroke-linecap="round"
								stroke-linejoin="round"
								d="M15.182 16.318A4.486 4.486 0 0012.016 15a4.486 4.486 0 00-3.198 1.318M21 12a9 9 0 11-18 0 9 9 0 0118 0zM9.75 9.75c0 .414-.168.75-.375.75S9 10.164 9 9.75 9.168 9 9.375 9s.375.336.375.75zm-.375 0h.008v.015h-.008V9.75zm5.625 0c0 .414-.168.75-.375.75s-.375-.336-.375-.75.168-.75.375-.75.375.336.375.75zm-.375 0h.008v.015h-.008V9.75z"
							/>
						</svg>
					{/if}
				</h2>
				{#if data.health.components.r2dbc.status === 'UP'}
					Database : {data.health.components.r2dbc.details.database}
				{:else}
					Error reason :
					<code>{data.health.components.r2dbc.details.error}</code>
				{/if}
				<h2 class="mt-4 text-base font-semibold first:mt-0">
					Disk usage
					{#if data.health.components.diskSpace.status === 'UP'}
						<svg
							xmlns="http://www.w3.org/2000/svg"
							fill="none"
							viewBox="0 0 24 24"
							stroke-width="1.5"
							stroke="currentColor"
							class="w-6 h-6 text-success inline"
						>
							<path
								stroke-linecap="round"
								stroke-linejoin="round"
								d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
							/>
						</svg>
					{:else}
						<svg
							xmlns="http://www.w3.org/2000/svg"
							fill="none"
							viewBox="0 0 24 24"
							stroke-width="1.5"
							stroke="currentColor"
							class="w-6 h-6 text-danger inline"
						>
							<path
								stroke-linecap="round"
								stroke-linejoin="round"
								d="M15.182 16.318A4.486 4.486 0 0012.016 15a4.486 4.486 0 00-3.198 1.318M21 12a9 9 0 11-18 0 9 9 0 0118 0zM9.75 9.75c0 .414-.168.75-.375.75S9 10.164 9 9.75 9.168 9 9.375 9s.375.336.375.75zm-.375 0h.008v.015h-.008V9.75zm5.625 0c0 .414-.168.75-.375.75s-.375-.336-.375-.75.168-.75.375-.75.375.336.375.75zm-.375 0h.008v.015h-.008V9.75z"
							/>
						</svg>
					{/if}
				</h2>
				<ul class="my-2 list-inside list-disc text-sm text-ink-muted">
					<li>
						{#if data.health.components.diskSpace.status === 'UP'}
							Free space : {Math.round(
								(data.health.components.diskSpace.details.free * 100) /
									data.health.components.diskSpace.details.total
							)}
							%
						{:else}
							Error reason :
							<code>{data.health.components.diskSpace.details.error}</code>
						{/if}
					</li>
				</ul>
			</div>
		</div>
		<div class="card my-5 min-w-72 flex-1 overflow-hidden">
			<div class="w-full border-b border-line bg-canvas px-6 py-4">
				<h2 class="text-xl font-semibold">Runtime</h2>
			</div>
			<div class="stat-panel p-6">
				<h2 class="mt-4 text-base font-semibold first:mt-0">Java</h2>
				<ul class="my-2 list-inside list-disc text-sm text-ink-muted">
					<li>JVM version : {data.info.java.version}</li>
					<li>JVM name : {data.info.java.runtime.name}</li>
					<li>JVM vendor : {data.info.java.vendor.version}</li>
				</ul>
				<h2 class="mt-4 text-base font-semibold first:mt-0">OS</h2>
				<ul class="my-2 list-inside list-disc text-sm text-ink-muted">
					<li>OS name : {data.info.os.name}</li>
					<li>OS arch : {data.info.os.arch}</li>
					<li>Available Processors : {data.info.runtime.availableProcessors}</li>
					<li>Memory : {Math.round(data.info.runtime.maxMemory / 1000000000)} Go</li>
				</ul>
			</div>
		</div>
		<div class="card my-5 min-w-72 flex-1 overflow-hidden">
			<div class="w-full border-b border-line bg-canvas px-6 py-4">
				<h2 class="text-xl font-semibold">Build</h2>
			</div>
			<div class="stat-panel p-6">
				<h2 class="mt-4 text-base font-semibold first:mt-0">Last update</h2>
				<ul class="my-2 list-inside list-disc text-sm text-ink-muted">
					<li>
						Backend : <Time
							timestamp={data.info.build.time || ''}
							format="dddd D MMMM YYYY @ h:mm A "
						/>
					</li>
					<li>
						Frontend : <Time timestamp={frontendBuildDate} format="dddd D MMMM YYYY @ h:mm A " />
					</li>
				</ul>
				<h2 class="mt-4 text-base font-semibold first:mt-0">Maven coordinates</h2>
				<ul class="my-2 list-inside list-disc text-sm text-ink-muted">
					<li>group : {data.info.build.group}</li>
					<li>artifact : {data.info.build.artifact}</li>
					<li>version : {data.info.build.version}</li>
				</ul>
				<h2 class="mt-4 text-base font-semibold first:mt-0">Git</h2>
				<ul class="my-2 list-inside list-disc text-sm text-ink-muted">
					<li>Git repository : {data.info.git.remote.origin.url}</li>
					<li>Git branch : {data.info.git.branch}</li>
					<li>Git commit id : {data.info.git.commit.id.abbrev}</li>
				</ul>
			</div>
		</div>
	</div>
</section>
