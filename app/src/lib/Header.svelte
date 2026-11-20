<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { onMount } from 'svelte';
	import AccountMenu from '#lib/AccountMenu.svelte';
	import { session } from '#lib/session.svelte.js';

	// Who is logged in, for the account menus (desktop and mobile)
	onMount(() => {
		session.load();
	});

	const links = [
		{ path: '/', label: 'Home' },
		{ path: '/tracks', label: 'Tracks' },
		{ path: '/about', label: 'About' }
	] as const;

	const linkClass = 'text-gray-300 hover:bg-gray-700 hover:text-white';
	const currentLinkClass = 'bg-gray-900 text-white';

	let mobileMenuVisible = $state(false);
	function toggleMobileMenuVisible() {
		mobileMenuVisible = !mobileMenuVisible;
	}
</script>

<header class="sticky top-0 z-50">
	<nav class="bg-gray-800">
		<div class="mx-auto max-w-7xl px-2 sm:px-6 lg:px-8">
			<div class="relative flex h-16 items-center justify-between">
				<div class="absolute inset-y-0 left-0 flex items-center sm:hidden">
					<!-- Mobile menu button-->
					<button
						type="button"
						class="inline-flex items-center justify-center rounded-md p-2 text-gray-400 hover:bg-gray-700 hover:text-white focus:outline-none focus:ring-2 focus:ring-inset focus:ring-white"
						aria-controls="mobile-menu"
						aria-expanded={mobileMenuVisible}
						onclick={toggleMobileMenuVisible}
					>
						<span class="sr-only">Open main menu</span>

						{#if !mobileMenuVisible}
							<!--
                          Icon when menu is closed.
                          Heroicon name: outline/bars-3
                          Menu open: "hidden", Menu closed: "block"
                        -->
							<svg
								xmlns="http://www.w3.org/2000/svg"
								fill="none"
								viewBox="0 0 24 24"
								stroke-width="1.5"
								stroke="currentColor"
								class="block w-6 h-6"
								aria-hidden="true"
							>
								<path
									stroke-linecap="round"
									stroke-linejoin="round"
									d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5"
								/>
							</svg>
						{:else}
							<!--
                          Icon when menu is open.
                          Heroicon name: outline/x-mark
                          Menu open: "block", Menu closed: "hidden"
                        -->
							<svg
								xmlns="http://www.w3.org/2000/svg"
								fill="none"
								viewBox="0 0 24 24"
								stroke-width="1.5"
								stroke="currentColor"
								class="block w-6 h-6"
								aria-hidden="true"
							>
								<path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
							</svg>
						{/if}
					</button>
				</div>
				<div class="flex flex-1 items-center justify-center sm:items-stretch sm:justify-start">
					<div class="flex flex-shrink-0 items-center">
						<img
							src="/images/icon-72x72.png"
							alt="Tune Lad"
							class="h-8 w-auto"
							width="64"
							height="64"
						/>
					</div>
					<div class="hidden sm:ml-6 sm:block">
						<div class="flex space-x-4">
							{#each links as link (link.path)}
								{@const current = page.url.pathname === link.path}
								<a
									href={resolve(link.path)}
									class="{current
										? currentLinkClass
										: linkClass} px-3 py-2 rounded-md text-sm font-medium"
									aria-current={current ? 'page' : undefined}>{link.label}</a
								>
							{/each}
						</div>
					</div>
				</div>
				<div class="hidden sm:ml-6 sm:flex sm:items-center">
					<AccountMenu />
				</div>
			</div>
		</div>

		<!-- Mobile menu, show/hide based on menu state. -->
		<div class={mobileMenuVisible ? 'sm:hidden' : 'hidden'} id="mobile-menu">
			<div class="space-y-1 px-2 pt-2 pb-3">
				{#each links as link (link.path)}
					{@const current = page.url.pathname === link.path}
					<a
						href={resolve(link.path)}
						class="{current
							? currentLinkClass
							: linkClass} block px-3 py-2 rounded-md text-base font-medium"
						aria-current={current ? 'page' : undefined}>{link.label}</a
					>
				{/each}
				<AccountMenu mobile />
			</div>
		</div>
	</nav>
</header>
