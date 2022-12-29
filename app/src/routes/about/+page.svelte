<script>
	import Time from 'svelte-time';

	/** @type {import('./$types').PageData} */
	export let data;
</script>

<svelte:head>
	<title>Tune Lad - About</title>
</svelte:head>

<div class="flex flex-col w-full">
	<h1 class="text-3xl font-medium leading-normal ">
		{data.info.build.name}
		<span style="font-size: small"
			>(Built on : <Time
				imestamp={data.info.build.time}
				format="dddd D MMMM YYYY @ h:mm A "
			/>)</span
		>
	</h1>
	<div
		class="my-5 block p-6 bg-white border border-gray-200 rounded-lg shadow-md dark:bg-gray-800 dark:border-gray-700"
	>
		<h2 class="text-2xl font-medium leading-6 ">
			Application Status
			{#if data.health.status === 'UP'}
				<i class="fa-solid fa-sun text-3xl text-yellow-400" />
			{:else}
				<i class="fa-regular fa-face-frown text-3xl text-red-700" />
			{/if}
		</h2>
		<dl>
			<dt class="mt-2 ">
				Database
				{#if data.health.components.db.status === 'UP'}
					<i class="fa-solid fa-circle-check text-green-600" />
				{:else}
					<i class="fa-regular fa-face-frown text-2xl text-red-700" />
				{/if}
			</dt>
			<dd>
				{#if data.health.components.db.status === 'UP'}
					Database type : {data.health.components.db.details.database}
				{:else}
					Error reason : <code>{data.health.components.db.details.error}</code>
				{/if}
			</dd>
			<dt class="mt-2 ">
				Elasticsearch
				{#if data.health.components.elasticsearch.status === 'UP'}
					<i class="fa-solid fa-circle-check text-green-600" />
				{:else}
					<i class="fa-regular fa-face-frown text-2xl text-red-700" />
				{/if}
			</dt>
			<dd>
				{#if data.health.components.elasticsearch.status === 'UP'}
					Cluster name : {data.health.components.elasticsearch.details.cluster_name}
				{:else}
					Error reason :
					<code>{data.health.components.elasticsearch.details.error}</code>
				{/if}
			</dd>
			<dt class="mt-2 ">
				Disk usage
				{#if data.health.components.diskSpace.status === 'UP'}
					<i class="fa-solid fa-circle-check text-green-600" />
				{:else}
					<i class="fa-regular fa-face-frown text-2xl text-red-700" />
				{/if}
			</dt>
			<dd>
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
			</dd>
		</dl>
	</div>
	<div
		class="my-5 block p-6 bg-white border border-gray-200 rounded-lg shadow-md dark:bg-gray-800 dark:border-gray-700"
	>
		<h2 class="text-2xl font-medium leading-6 ">Maven coordinates</h2>
		<ul class="my-2 list-disc list-inside">
			<li>group : {data.info.build.group}</li>
			<li>artifact : {data.info.build.artifact}</li>
			<li>version : {data.info.build.version}</li>
		</ul>
		<h2 class="text-2xl font-medium leading-6 ">Java runtime</h2>
		<ul class="my-2 list-disc list-inside">
			<li>Java version : {data.info.java.version}</li>
			<li>Java runtime name : {data.info.java.runtime.name}</li>
			<li>Java vendor : {data.info.java.vendor.version}</li>
		</ul>
		<h2 class="text-2xl font-medium leading-6 ">Git</h2>
		<ul class="my-2 list-disc list-inside">
			<li>Git branch : {data.info.git.branch}</li>
			<li>Git commit id : {data.info.git.commit.id}</li>
		</ul>
	</div>
</div>
