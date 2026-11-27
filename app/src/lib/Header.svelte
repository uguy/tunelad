<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { onMount } from 'svelte';
	import AccountMenu from '#lib/AccountMenu.svelte';
	import Icon from '#lib/Icon.svelte';
	import ThemeSwitch from '#lib/ThemeSwitch.svelte';
	import { session } from '#lib/session.svelte.js';

	// Who is logged in, for the account menus (desktop and mobile)
	onMount(() => {
		session.load();
	});

	const links = [
		{ path: '/', label: 'Home' },
		{ path: '/tracks', label: 'Tracks' }
	] as const;

	/** The Tracks link also stands for the pages of the collections below it. */
	function isCurrent(path: string) {
		const here = page.url.pathname;
		return path === '/' ? here === '/' : here === path || here.startsWith(`${path}/`);
	}

	let mobileMenuVisible = $state(false);

	// A page change closes the mobile menu
	$effect(() => {
		void page.url.pathname;
		mobileMenuVisible = false;
	});
</script>

<header class="sticky top-0 z-50">
	<nav class="border-b border-on-header/10 bg-header shadow-md">
		<div class="mx-auto max-w-7xl px-2 sm:px-6 lg:px-8">
			<div class="relative flex h-14 items-center justify-between">
				<div class="absolute inset-y-0 left-0 flex items-center sm:hidden">
					<button
						type="button"
						class="inline-flex size-11 items-center justify-center rounded-md text-on-header/70 hover:bg-on-header/10 hover:text-on-header"
						aria-controls="mobile-menu"
						aria-expanded={mobileMenuVisible}
						onclick={() => (mobileMenuVisible = !mobileMenuVisible)}
					>
						<span class="sr-only">Open main menu</span>
						<Icon name={mobileMenuVisible ? 'close' : 'menu'} class="h-6 w-6" />
					</button>
				</div>
				<div class="flex flex-1 items-center justify-center sm:items-stretch sm:justify-start">
					<a href={resolve('/')} class="flex shrink-0 items-center gap-2">
						<img
							src="/images/icon-72x72.png"
							alt="Tune Lad"
							class="h-8 w-auto rounded"
							width="64"
							height="64"
						/>
						<span class="hidden text-lg font-bold tracking-tight text-on-header md:inline">
							Tune Lad
						</span>
					</a>
					<div class="hidden sm:ml-8 sm:flex">
						<div class="flex h-full space-x-1">
							{#each links as link (link.path)}
								{@const current = isCurrent(link.path)}
								<a
									href={resolve(link.path)}
									class="flex items-center border-b-2 px-4 text-sm font-semibold transition-colors {current
										? 'border-accent text-on-header'
										: 'border-transparent text-on-header/70 hover:text-on-header'}"
									aria-current={current ? 'page' : undefined}
								>
									{link.label}
								</a>
							{/each}
						</div>
					</div>
				</div>
				<div class="hidden sm:ml-6 sm:flex sm:items-center sm:gap-2">
					<ThemeSwitch />
					<AccountMenu />
				</div>
			</div>
		</div>

		<!-- Mobile menu, show/hide based on menu state. -->
		<div class={mobileMenuVisible ? 'sm:hidden' : 'hidden'} id="mobile-menu">
			<div class="space-y-1 px-2 pt-2 pb-3">
				{#each links as link (link.path)}
					{@const current = isCurrent(link.path)}
					<a
						href={resolve(link.path)}
						class="block rounded-md px-3 py-2 text-base font-medium {current
							? 'bg-on-header/10 text-on-header'
							: 'text-on-header/70 hover:bg-on-header/10 hover:text-on-header'}"
						aria-current={current ? 'page' : undefined}
					>
						{link.label}
					</a>
				{/each}
				<div class="my-2 border-t border-on-header/10"></div>
				<ThemeSwitch mobile />
				<AccountMenu mobile />
			</div>
		</div>
	</nav>
</header>
