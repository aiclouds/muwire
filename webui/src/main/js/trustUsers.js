class Persona {
	constructor(xmlNode) {
		this.user = xmlNode.getElementsByTagName("User")[0].childNodes[0].nodeValue
		this.userB64 = xmlNode.getElementsByTagName("UserB64")[0].childNodes[0].nodeValue
		this.subscribed = xmlNode.getElementsByTagName("Subscribed")[0].childNodes[0].nodeValue
		this.reason = ""
		try {
			this.reason = xmlNode.getElementsByTagName("Reason")[0].childNodes[0].nodeValue
		} catch (ignore) {}
	}
	
	getMapping(trusted) {
		var mapping = new Map()
		var nameHtml = this.user
		if (trusted) {
			nameHtml += this.getNeutralLink()
			nameHtml += this.getDistrustedBlock()
		} else {
			nameHtml += this.getTrustedBlock()
			nameHtml += this.getNeutralLink()
		}
		
		mapping.set("User", nameHtml)
		mapping.set("Reason", this.reason)
		
		if (trusted) {
			var subscribeHtml = _t("Subscribed")
			if (this.subscribed != "true")
				subscribeHtml = this.getSubscribeLink()
			mapping.set("Subscribe", subscribeHtml)
		}
		
		return mapping
	}
	
	getTrustedBlock() {
		var link = "<a herf='#' onclick='window.markTrusted(\"" + this.userB64 + "\"); return false;'>" + _t("Mark Trusted") + "</a>"
		var block = "<span id='trusted-link-" + this.userB64 + "'>" + link + "</span>"
		block += "<span id='trusted-" + this.userB64 + "'></span>"
		return block
	}
	
	getNeutralLink() {
		return "<a herf='#' onclick='window.markNeutral(\"" + this.userB64 + "\"); return false;'>" + _t("Mark Neutral") + "</a>"
	}
	
	getDistrustedBlock() {
		var link = "<a herf='#' onclick='window.markDistrusted(\"" + this.userB64 + "\"); return false;'>" + _t("Mark Distrusted") + "</a>"
		var block = "<span id='distrusted-link-" + this.userB64 + "'>" + link + "</span>"
		block += "<span id='distrusted-" + this.userB64 + "'></span>"
		return block
	}
	
	getSubscribeLink() {
		return "<a href='#' onclick='window.subscribe(\"" + this.userB64 + "\"); return false;'>" + _t("Subscribe") + "</a>"
	}
} 

var trusted = new Map()
var distrusted = new Map()
var revision = -1

var trustedUsersSortKey
var trustedUsersSortOrder
var distrustedUsersSortKey
var distrustedUsersSortOrder

function subscribe(host) {
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			refreshUsers()
		}
	}
	xmlhttp.open("POST","/MuWire/Trust", true)
	xmlhttp.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');
	xmlhttp.send("action=subscribe&persona=" + host)
}

function markTrusted(host) {
	var linkSpan = document.getElementById("trusted-link-"+host)
	linkSpan.innerHTML = ""
	
	var textAreaSpan = document.getElementById("trusted-"+host)
	
	var textbox = "<textarea id='trust-reason-" + host + "'></textarea>"
	var submitLink = "<a href='#' onclick='window.submitTrust(\"" + host + "\");return false;'>" + _t("Submit") + "</a>"
	var cancelLink = "<a href='#' onclick='window.cancelTrust(\"" + host + "\");return false;'>" + _t("Cancel") + "</a>"
	
	var html = "<br/>" + _t("Enter Reason (Optional)") + "<br/>" + textbox + "<br/>" + submitLink + " " + cancelLink + "<br/>"
	
	textAreaSpan.innerHTML = html
}

function markNeutral(host) {
	publishTrust(host, "", "neutral")
}

function markDistrusted(host) {
	var linkSpan = document.getElementById("distrusted-link-"+host)
	linkSpan.innerHTML = ""
	
	var textAreaSpan = document.getElementById("distrusted-"+host)
	
	var textbox = "<textarea id='distrust-reason-" + host + "'></textarea>"
	var submitLink = "<a href='#' onclick='window.submitDistrust(\"" + host + "\");return false;'>" + _t("Submit") + "</a>"
	var cancelLink = "<a href='#' onclick='window.cancelDistrust(\"" + host + "\");return false;'>" + _t("Cancel") + "</a>"
	
	var html = "<br/>" + _t("Enter Reason (Optional)") + "<br/>" + textbox + "<br/>" + submitLink + " " + cancelLink + "<br/>"
	
	textAreaSpan.innerHTML = html
}

function submitTrust(host) {
	var reason = document.getElementById("trust-reason-"+host).value
	publishTrust(host, reason, "trust")
}

function submitDistrust(host) {
	var reason = document.getElementById("distrust-reason-"+host).value
	publishTrust(host, reason, "distrust")
}


function publishTrust(host, reason, trust) {
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			refreshUsers()
		}
	}
	xmlhttp.open("POST","/MuWire/Trust", true)
	xmlhttp.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');
	xmlhttp.send("action=" + trust + "&reason=" + reason + "&persona=" + host)
}

function cancelTrust(host) {
	var textAreaSpan = document.getElementById("trusted-" + host)
	textAreaSpan.innerHTML = ""
	
	var linkSpan = document.getElementById("trusted-link-"+host)
	var html = "<a href='#' onclick='markTrusted(\"" + host + "\"); return false;'>" + _t("Mark Trusted") + "</a>"
	linkSpan.innerHTML = html
}

function cancelDistrust(host) {
	var textAreaSpan = document.getElementById("distrusted-" + host)
	textAreaSpan.innerHTML = ""
	
	var linkSpan = document.getElementById("distrusted-link-"+host)
	var html = "<a href='#' onclick='markDistrusted(\"" + host + "\"); return false;'>" + _t("Mark Distrusted") + "</a>"
	linkSpan.innerHTML = html
}

function sortTrustedUsers(key, order) {
	trustedUsersSortKey = key
	trustedUsersSortOrder = order
	refreshTrustedUsers()
}

function sortDistrustedUsers(key, order) {
	distrustedUsersSortKey = key
	distrustedUsersSortOrder = order
	refreshDistrustedUsers()
}

function refreshDistrustedUsers() {
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			distrusted.clear()
			var distrustedList = []
			
			var distrustedNodes = this.responseXML.getElementsByTagName("Persona")
			var i
			for (i = 0; i < distrustedNodes.length; i++) {
				var persona = new Persona(distrustedNodes[i])
				distrusted.set(persona.user, persona)
				distrustedList.push(persona)
			}
			
			var newOrder
			if (distrustedUsersSortOrder == "descending")
				newOrder = "ascending"
			else if (distrustedUsersSortOrder == "ascending")
				newOrder = "descending"
			var table = new Table(["User", "Reason"], "sortDistrustedUsers", distrustedUsersSortKey, newOrder)
			
			for (i = 0; i < distrustedList.length; i++) {
				table.addRow(distrustedList[i].getMapping(false))
			}
			
			var tableDiv = document.getElementById("distrustedUsers")
			tableDiv.innerHTML = table.render()
		}
	}
	var sortParam = "&key=" + distrustedUsersSortKey + "&order=" + distrustedUsersSortOrder
	xmlhttp.open("GET", "/MuWire/Trust?section=distrustedUsers" + sortParam)
	xmlhttp.send()
}

function refreshTrustedUsers() {
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			trusted.clear()
			var trustedList = []
			
			var trustedNodes = this.responseXML.getElementsByTagName("Persona")
			var i
			for (i = 0; i < trustedNodes.length; i++) {
				var persona = new Persona(trustedNodes[i])
				trusted.set(persona.user, persona)
				trustedList.push(persona)
			}
			
			var newOrder
			if (trustedUsersSortOrder == "descending")
				newOrder = "ascending"
			else if (trustedUsersSortOrder == "ascending")
				newOrder = "descending"
			var table = new Table(["User" , "Reason", "Subscribe"], "sortTrustedUsers", trustedUsersSortKey, newOrder)
			
			for (i = 0; i < trustedList.length; i++) {
				table.addRow(trustedList[i].getMapping(true))
			}
			
			var tableDiv = document.getElementById("trustedUsers")
			tableDiv.innerHTML = table.render()
		}
	}
	var sortParam = "&key=" + trustedUsersSortKey + "&order=" + trustedUsersSortOrder
	xmlhttp.open("GET", "/MuWire/Trust?section=trustedUsers" + sortParam)
	xmlhttp.send()
}

function refreshUsers() {
	refreshTrustedUsers()
	refreshDistrustedUsers()
}

function fetchRevision() {
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			var xmlDoc = this.responseXML
			var newRevision = xmlDoc.childNodes[0].childNodes[0].nodeValue
			if (newRevision > revision) {
				revision = newRevision
				refreshUsers()
			}
		}
	}
	xmlhttp.open("GET", "/MuWire/Trust?section=revision", true)
	xmlhttp.send()
}

function initTrustUsers() {
	setTimeout(fetchRevision, 1)
	setInterval(fetchRevision, 3000)
}