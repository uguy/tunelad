<script>
	import Time from 'svelte-time';

	/** @type {import('./$types').PageData} */
	export let data;

	console.log(data);
</script>

<svelte:head>
	<title>Tune Lad - About</title>
</svelte:head>

<div class="row">
	<div class="col-md-2" />
	<div class="col-md-8">
		<h1>
			{data.info.build.name}
			<span style="font-size: small"
				>(Built on : <Time
					imestamp={data.info.build.time}
					format="dddd D MMMM YYYY @ h:mm A "
				/>)</span
			>
		</h1>
		<div class="shadow p-3 mb-5 bg-body rounded">
			<h4>
				Application Status
				{#if data.health.status === 'UP'}
					<i class="bi bi-brightness-high-fill text-warning " />
				{:else}
					<i class="bi bi-cloud-drizzle-fill text-alert" />
				{/if}
			</h4>
			<dl>
				<dt>
					Database {#if data.health.components.db.status === 'UP'}
						<i class="bi bi-check-circle-fill text-success" />
					{:else}
						<i class="bi bi-cloud-drizzle-fill text-alert" />
					{/if}
				</dt>
				<dd>
					{#if data.health.components.db.status === 'UP'}
						Database type : {data.health.components.db.details.database}
					{:else}
						Error reason : <code>{data.health.components.db.details.error}</code>
					{/if}
				</dd>
				<dt>
					Elasticsearch
					{#if data.health.components.elasticsearch.status === 'UP'}
						<i class="bi bi-check-circle-fill text-success" />
					{:else}
						<i class="bi bi-cloud-drizzle-fill text-alert" />
					{/if}
				</dt>
				<dd>
					{#if data.health.components.elasticsearch.status === 'UP'}
						Cluster name : {data.health.components.elasticsearch.details.cluster_name}
					{:else}
						Error reason : <code>{data.health.components.elasticsearch.details.error}</code>
					{/if}
				</dd>
				<dt>
					Disk usage
					{#if data.health.components.diskSpace.status === 'UP'}
						<i class="bi bi-check-circle-fill text-success" />
					{:else}
						<i class="bi bi-cloud-drizzle-fill text-alert" />
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
						Error reason : <code>{data.health.components.diskSpace.details.error}</code>
					{/if}
				</dd>
			</dl>
			<hr />
			<h4>Maven coordinates</h4>
			<ul>
				<li>group : {data.info.build.group}</li>
				<li>artifact : {data.info.build.artifact}</li>
				<li>version : {data.info.build.version}</li>
			</ul>
			<h4>Java runtime</h4>
			<ul>
				<li>Java version : {data.info.java.version}</li>
				<li>Java runtime name : {data.info.java.runtime.name}</li>
				<li>Java vendor : {data.info.java.vendor.version}</li>
			</ul>
			<h4>Git</h4>
			<ul>
				<li>Git branch : {data.info.git.branch}</li>
				<li>Git commit id : {data.info.git.commit.id}</li>
			</ul>
		</div>
	</div>
	<div class="col-md-2" />
</div>

<style>
	.text-alert {
		color: #fd7e14;
	}
</style>
