<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';

	let currentPathName = $derived(page.url.pathname);

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
								class="{mobileMenuVisible ? 'hidden' : 'block'} w-6 h-6"
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
								class="{mobileMenuVisible ? 'block' : 'hidden'} w-6 h-6"
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
							class="block h-8 w-auto lg:hidden"
							width="64px"
							height="64px"
						/>
						<img
							src="/images/icon-72x72.png"
							alt="Tune Lad"
							class="hidden h-8 w-auto lg:block"
							width="64px"
							height="64px"
						/>
					</div>
					<div class="hidden sm:ml-6 sm:block">
						<div class="flex space-x-4">
							<!-- Current: "bg-gray-900 text-white", Default: "text-gray-300 hover:bg-gray-700 hover:text-white" -->
							{#if currentPathName === '/'}
								<a
									href={resolve('/')}
									class="bg-gray-900 text-white px-3 py-2 rounded-md text-sm font-medium"
									aria-current="page">Home</a
								>
							{:else}
								<a
									href={resolve('/')}
									class="text-gray-300 hover:bg-gray-700 hover:text-white px-3 py-2 rounded-md text-sm font-medium"
									>Home</a
								>
							{/if}
							{#if currentPathName === '/tracks'}
								<a
									href={resolve('/tracks')}
									class="bg-gray-900 text-white px-3 py-2 rounded-md text-sm font-medium"
									aria-current="page">Tracks</a
								>
							{:else}
								<a
									href={resolve('/tracks')}
									class="text-gray-300 hover:bg-gray-700 hover:text-white px-3 py-2 rounded-md text-sm font-medium"
									>Tracks</a
								>
							{/if}
							{#if currentPathName === '/about'}
								<a
									href={resolve('/about')}
									class="bg-gray-900 text-white px-3 py-2 rounded-md text-sm font-medium"
									aria-current="page">About</a
								>
							{:else}
								<a
									href={resolve('/about')}
									class="text-gray-300 hover:bg-gray-700 hover:text-white px-3 py-2 rounded-md text-sm font-medium"
									>About</a
								>
							{/if}
						</div>
					</div>
				</div>
			</div>
		</div>

		<!-- Mobile menu, show/hide based on menu state. -->
		<div class={mobileMenuVisible ? 'sm:block block' : 'sm:hidden hidden'} id="mobile-menu">
			<div class="space-y-1 px-2 pt-2 pb-3">
				<!-- Current: "bg-gray-900 text-white", Default: "text-gray-300 hover:bg-gray-700 hover:text-white" -->
				{#if currentPathName === '/'}
					<a
						href={resolve('/')}
						class="bg-gray-900 text-white block px-3 py-2 rounded-md text-base font-medium"
						aria-current="page">Home</a
					>
				{:else}
					<a
						href={resolve('/')}
						class="text-gray-300 hover:bg-gray-700 hover:text-white block px-3 py-2 rounded-md text-base font-medium"
						>Home</a
					>
				{/if}
				{#if currentPathName === '/tracks'}
					<a
						href={resolve('/tracks')}
						class="bg-gray-900 text-white block px-3 py-2 rounded-md text-base font-medium"
						aria-current="page">Tracks</a
					>
				{:else}
					<a
						href={resolve('/tracks')}
						class="text-gray-300 hover:bg-gray-700 hover:text-white block px-3 py-2 rounded-md text-base font-medium"
						>Tracks</a
					>
				{/if}
				{#if currentPathName === '/about'}
					<a
						href={resolve('/about')}
						class="bg-gray-900 text-white block px-3 py-2 rounded-md text-base font-medium"
						aria-current="page">About</a
					>
				{:else}
					<a
						href={resolve('/about')}
						class="text-gray-300 hover:bg-gray-700 hover:text-white block px-3 py-2 rounded-md text-base font-medium"
						>About</a
					>
				{/if}
			</div>
		</div>
	</nav>
</header>
