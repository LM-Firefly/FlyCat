if (location.hostname === 'flycat.mintlify.app') {
	const url = new URL(location.href)
	location.replace(((url.hostname = 'FlyCat.gal.tf'), url))
}