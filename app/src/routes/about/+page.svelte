<script>
	/** @type {import('./$types').PageData} */
	export let data;

	console.log(data);
</script>

<svelte:head>
	<title>Tune Lad - About</title>
</svelte:head>

<div class="row">
	<div class="col-md-3" />
	<div class="col-md-6">
		<h1>About</h1>
		<div class="shadow p-3 mb-5 bg-body rounded">
			<p>Information about {data.info.build.name} application build on {data.info.build.time}</p>

			<ul>
				<li>group : {data.info.build.group}</li>
				<li>artifact : {data.info.build.artifact}</li>
				<li>version : {data.info.build.version}</li>
			</ul>
		</div>
	</div>
</div>
