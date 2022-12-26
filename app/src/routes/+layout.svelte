<nav class="navbar navbar-dark bg-dark">
	<div class="container-fluid">
		<a class="navbar-brand" href="/">
			<img
				src="images/tracks.png"
				alt=""
				width="30"
				height="24"
				class="d-inline-block align-text-top"
			/>
			Tune Lad
		</a>
		<button
			class="navbar-toggler"
			type="button"
			data-bs-toggle="collapse"
			data-bs-target="#navbarNav"
			aria-controls="navbarNav"
			aria-expanded="false"
			aria-label="Toggle navigation"
		>
			<span class="navbar-toggler-icon" />
		</button>
		<div class="collapse navbar-collapse" id="navbarNav">
			<ul class="navbar-nav">
				<li class="nav-item">
					<a class="nav-link active" aria-current="page" href="/tracks">Tracks</a>
				</li>
				<li class="nav-item">
					<a class="nav-link" href="/about">About</a>
				</li>
			</ul>
		</div>
	</div>
</nav>

<div class="row">
	<div class="col-md-1" />
	<div class="col-md-10" style="background-color: #fff; padding-bottom: 2em"><slot /></div>
	<div class="col-md-1" />
</div>
