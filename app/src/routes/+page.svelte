<svelte:head>
	<title>Tune Lad</title>
</svelte:head>

<div class="row">
	<div class="col-md-3" />
	<div class="col-md-9">
		<h1 class="display-4">Welcome to Tune Lad !</h1>
		<p class="lead">Start listening to some <a href="tracks">tracks</a> ...</p>
		<span class="wall-img img-fluid rounded" />
	</div>
</div>

<style>
	.wall-img {
		display: inline-block;
		width: 547px;
		height: 697px;
		background: url('celtic-connections.png') no-repeat center top;
		background-size: contain;
	}

	/* wait autoprefixer update to allow simple generation of high pixel density media query */
	@media only screen and (-webkit-min-device-pixel-ratio: 2),
		only screen and (-moz-min-device-pixel-ratio: 2),
		only screen and (-o-min-device-pixel-ratio: 2/1),
		only screen and (min-resolution: 192dpi),
		only screen and (min-resolution: 2dppx) {
		.wall-img {
			background: url('celtic-connections.png') no-repeat center top;
			background-size: contain;
		}
	}
</style>
