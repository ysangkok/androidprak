var createTab = function(tab, serial){

var opts = {
  lines: 16, // The number of lines to draw
  length: 7, // The length of each line
  width: 4, // The line thickness
  radius: 10, // The radius of the inner circle
  color: '#000', // #rgb or #rrggbb
  speed: 1, // Rounds per second
  trail: 60, // Afterglow percentage
  shadow: false // Whether to render a shadow
};

var img = $(".camera", tab);
var imgspinner = img.parent().spin();

var log = function(str) {
  $("#loggingwindow").append("[" + serial + "]: " + str + "\n");
  $("#loggingwindow").prop({ scrollTop: $("#loggingwindow").prop("scrollHeight") });
};

var mmsend = function(str) {
  if (ws.send(str))
    log("sent: " + str);
  else
    log("couldn't send: " + str);
};

  $(".code", tab).attr("id", "code-" + serial);

  var editor = CodeMirror.fromTextArea(document.getElementById("code-" + serial), {
    mode: "clike"
  });

var ws = new WebSocket("ws://localhost:9884/ws/subscribe?phone=" + serial);
ws.onopen = function (evt) {
	log("open");
	mmsend("getwaitimg");
};
ws.onmessage = function (evt) {
	log("webcam got data: " + evt.data.length);
	imgspinner.spin(false);
	img.attr("src", evt.data);
};
ws.onclose = function (evt) {
	log("closed");
}
var msgs = $( ".msgs", tab );

/*
tab.append($("<button>").append("getwaitimg").on("click",function(){
	mmsend("getwaitimg");
}));
*/

var replurls = function(text) {
    var exp = /(\b(https?|ftp|file):\/\/[-A-Z0-9+&@#\/%?=~_|!:,.;]*[-A-Z0-9+&@#\/%=~_|])/i;
    return text.replace(exp,"<a href='$1' target='_blank'>Link</a>"); 
};

    var submitcommand = function() {
        var postdata = {serial: serial, command: $(".command", tab).val()} ;
        var cont = $(".commandresult", tab);
	cont.children().css("visibility", "hidden");
        cont.spin();
        $.postJSON('/executecommand', postdata, function(data) {
            cont.spin(false);
	    cont.empty();
	    cont.append(data.time,$("<br>"));
            if (data.output.type == "location") {
              var la = parseFloat(data.output.data.latitude), lo = parseFloat(data.output.data.longitude);
              var height = 400, width = 1.61803399 * height;
              var map = $("<iframe>").attr("src","/getmap?la=" + la + "&" + "lo=" + lo).css("width",width + "px").css("height",height + "px");
	      var d = $("<div>");
	      cont.append(map,d);
              d.append($("<pre>").html(prettify(data.output.data)));
            } else if (data.output.type == "text") {
              cont.append($("<pre>").text(data.output.data));
	    } else if (data.output.type == "table") {
		var table = $( '<table cellpadding="0" cellspacing="0" border="0" class="display" id="example"></table>' );
		cont.append(table);

		var columnsarr = [];

		var columns = [];
		$.each(data.output.data[0], function (k,v) {
			columns.push(
				{
					"sTitle": k,
					"fnRender": function(obj) {
			                    var sReturn = obj.aData[ obj.iDataColumn ];
					    //console.log(this["sTitle"] + ": " + sReturn);
					    if (this["sTitle"] in {"date":"", "dtstart":""}) {
						return new Date(parseInt(sReturn));
					    } else {
					    	sReturn = replurls(sReturn);
			                    	return sReturn;
					    }
			                },
					"bUseRendered": true // returned object is Date and should be sortable. when we use original data (String of unixtime*1000) it wouldnt
				}
			);
			columnsarr.push(k);
		});
		//log(prettify(columns));

		var odata = [];
		$.each(data.output.data, function(index, v) {
			var arr = [];
/*
			$.each(v, function (k,v) {
				arr.push(v)	
			});
*/
			$.each(columnsarr, function(index, k) {
				if (k in v)
					arr.push(v[k]);
				else
					arr.push("n/a");
			});
			odata.push(arr);
		});
		//log(prettify(odata));


		table.dataTable( {
			"aaData": odata, "aoColumns": columns
		} );	
/*
		"aaData": [
			[ "Trident", "Internet Explorer 4.0", "Win 95+", 4, "X" ],
			[ "Gecko", "Firefox 1.5", "Win 98+ / OSX.2+", 1.8, "A" ],
			[ "Webkit", "Safari 1.2", "OSX.3", 125.5, "A" ],
		],
		"aoColumns": [
			{ "sTitle": "Engine" },
			{ "sTitle": "Browser" },
			{ "sTitle": "Platform" },
			{ "sTitle": "Version", "sClass": "center" },
			{
				"sTitle": "Grade",
			}
		]
*/
	    }
        });
    };

    $(".commandexecute", tab).on("click", submitcommand);
    $(".command", tab).keypress(function(e)
        {
            code= (e.keyCode ? e.keyCode : e.which);
            if (code == 13) {
		submitcommand();
            	e.preventDefault();
	    }
        });


var prettify = function(obj) {
var str = JSON.stringify(obj, undefined, 2);
str = str.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function (match) {
    var cls = 'number';
    if (/^"/.test(match)) {
        cls = 'string';
    } else if (/true|false/.test(match)) {
        cls = 'boolean';
    } else if (/null/.test(match)) {
        cls = 'null';
    }
    return '<span class="' + cls + '">' + match + '</span>';
});
return str;
};

    $(".codeexecute", tab).on("click", function() {
        var postdata = {serial: serial, code: editor.getValue(), classname: $(".classname", tab).val()} ;
	$(".coderesult", tab).children().css("visibility", "hidden");
        var s = $(".coderesult", tab).spin();
        $.postJSON('/executecode', postdata, function(data) {
            s.spin(false);
            $(".coderesult", tab).empty();
	    $(".coderesult", tab).append(data["time"] , ":" , $("<pre>").append(prettify(data['output']))) ;
        });
    });

/*
    $(".setupforwarding", tab).on("click", function() {
        var postdata = {port: $("#port").val()} ;
        $.postJSON('/setupforwarding', postdata, function(data) {
        });
    });
*/

$(".recstart",tab).on("click", function() {
	mmsend("recstart");
});
$(".recstop",tab).on("click", function() {
	mmsend("recstop");
});

var realtime = function(obj) {
	var listitems = [];
	for (var k in obj[1]) {
		var dl = $("<li>");
		var v = obj[1][k];
		var dt = $("<strong>");
		dt.text(k);
		var dd = $("<span>");
		dd.text(v);
		dl.append(dt,": ",dd);
		listitems.push(dl);
	}
	var ul = $("<ul>").css("display","none");
	$.each(listitems,function(index,v) { ul.append(v);});
	var onclick = function(e) {
		$("~ ul",this).toggle();
		e.preventDefault();
	};
	msgs.append($("<li>")
		.append($("<a href='javascript:void(0);'>").on("click",onclick).append(obj[0]))
		.append(ul)
	);
/*
        msgs.accordion( "destroy" );
        msgs.accordion();
*/
};

var commandlistupdate = function(obj) {
        log("setting autocomplete: " + obj[2]);
	var autocomp = $(".command", tab).autocomplete({source: obj[1], minLength: 0, delay: 0});
        autocomp.on("focus", function() { autocomp.autocomplete("search"); });
};

var setstatusfields = function(obj) {
	$(".statuslabel", tab).text(obj[1].status);
	$(".seriallabel", tab).text(obj[1].serial);
	$(".videostatuslabel", tab).text(obj[1].videostatus);
	$(".iplabel", tab).text(obj[1].ip);
	$(".portlabel", tab).text(obj[1].port);

	if (obj[1].ip == "127.0.0.1" && obj[1].port == 8080) {
		$(".ipportlabel",tab).append(
			$("<button>").on("click", function(evt) {
				$.postJSON("/setupforwarding", {serial:serial}, function(data) {
					if (data.type == "success")  {
						$(".portlabel", tab).text(data.data);
						$(".ipportlabel", tab).find("button").remove();
					} else {
						alertManager.show(data.data);
					}
				});
			}).append("Setup portforwarding")
		);
	}
};

var newvideo = function(obj) {
	$(".videos",tab).append($("<li>").append($("<a>").attr("href","../" + obj[1]).append(obj[1])));
};

var AssertException = function(message) { this.message = message; }
AssertException.prototype.toString = function () {
  return 'AssertException: ' + this.message;
}

var assert = function(exp, message) {
  if (!exp) {
    throw new AssertException(message);
  }
}

return function(obj) {
	//log(prettify(obj));
	if (obj[0] != "phonestatusupdate") assert(obj[2] == serial, "Got message that wasn't designated for this tab!");
	switch (obj[0]) {

		case "location":
		case "phone":
		case "sms":
			realtime(obj);
			break;
		case "commandlistupdate":
			commandlistupdate(obj);
			break;
		case "phonestatusupdate":
			setstatusfields(obj);
			break;
		case "newvideo":
			newvideo(obj);
			break;
		default:
			log("don't know command: " + obj[0]);
			break;
	}

};

};
