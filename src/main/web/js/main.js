//var serverUrlPrefix = "http://54.235.24.244:8983/solr/media_shard1_replica1/lireq";
//var searchUrlPrefix = "http://54.235.24.244:8983/solr/media_shard1_replica1/select";

var serverUrlPrefix = "http://localhost:8983/solr/media_shard1_replica1/lireq";
var searchUrlPrefix = "http://localhost:8983/solr/media_shard1_replica1/select";

function reWriteImageUrl(imgUrlOriginal) {
    imgUrl = "http://localhost/images" + imgUrlOriginal;
//    imgUrl = "http://107.20.76.134/images" + imgUrlOriginal;
    return imgUrl;
}

function getCBIRLinks(myID) {
    result = "";
    result += "<div style=\"font-size:8pt;text-transform:lowercase;\"><p>";
    result += "<a href=\"javascript:search('" + myID + "', 'ta_ha');\">ta</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'ga_ha');\">ga</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'll_ha');\">ll</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'fo_ha');\">fo</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'fc_ha');\">fc</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'jh_ha');\">jh</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'oh_ha');\">oh</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'jp_ha');\">jp</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'fo_ha');\">fo</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'cl_ha');\">CL</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'ce_ha');\">CE</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'eh_ha');\">EH</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'jc_ha');\">JC</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'ph_ha');\">PH</a> ";
    result += "<a href=\"javascript:search('" + myID + "', 'sc_ha');\">SC</a> ";
    result += "</p><p>"
    result += "<a href=\"javascript:hashSearch('ta','" + imageUrl + "');\">ta</a> "
    result += "<a href=\"javascript:hashSearch('ga','" + imageUrl + "');\">ga</a> "
    result += "<a href=\"javascript:hashSearch('ll','" + imageUrl + "');\">ll</a> "
    result += "<a href=\"javascript:hashSearch('fc','" + imageUrl + "');\">fc</a> "
    result += "<a href=\"javascript:hashSearch('fo','" + imageUrl + "');\">fo</a> "
    result += "<a href=\"javascript:hashSearch('jh','" + imageUrl + "');\">jh</a> "
    result += "<a href=\"javascript:hashSearch('oh','" + imageUrl + "');\">oh</a> "
    result += "<a href=\"javascript:hashSearch('jp','" + imageUrl + "');\">jp</a> "
    result += "<a href=\"javascript:hashSearch('cl','" + imageUrl + "');\">CL</a> "
    result += "<a href=\"javascript:hashSearch('ce','" + imageUrl + "');\">CE</a> "
    result += "<a href=\"javascript:hashSearch('eh','" + imageUrl + "');\">EH</a> "
    result += "<a href=\"javascript:hashSearch('jc','" + imageUrl + "');\">JC</a> "
    result += "<a href=\"javascript:hashSearch('ph','" + imageUrl + "');\">PH</a> "
    result += "<a href=\"javascript:hashSearch('sc','" + imageUrl + "');\">SC</a></p></div>"
    return result;
}

function printResults(docs) {
    var last = $("#results");
    wrapper = $("<div class=\"ui-grid-c\"></div>");
    wrapper.insertAfter(last);
    last = wrapper;
    for (var i = 0; i < docs.length; i++) {
        myID = docs[i].id.toString();
        imageUrl = reWriteImageUrl(myID);
        var col = "ui-block-a";
        if (i % 4 == 1) {
            col = "ui-block-b";
        } else if (i % 4 == 2) {
            col = "ui-block-c";
        } else if (i % 4 == 3) {
            col = "ui-block-d";
        }
        recent = $("<div class=\"" + col + "\"><div style=\"height:170px\"><img style=\"max-width:160px;max-height:160px;display: block;margin-left: auto;margin-right: auto;\" src=\"" + imageUrl + "\" /></div>"
                + "score=" + (docs[i].d || 0)
                + getCBIRLinks(myID)
                + "</div>");
        last.append(recent);
        //last=recent;
    }
}

function clearData() {
    $(".ui-grid-c").remove();
    $("#perf").html("Please stand by .... <img src=\"img/loader-light.gif\"/>");
}

function tagSearchDo() {
    console.log($('#tagsearch').val());
    // clear the old data
    clearData();
    queryString = searchUrlPrefix + "?q=" + $('#tagsearch').val() + "&wt=json&rows=60";
    console.log($('input[name="radio-v-1"]:checked').val());
    if ($('#sorthist').val()) {
        if ($('input[name="radio-v-1"]:checked').val() == "boost") {
            queryString = queryString + "&defType=edismax&boost=div(recip(lirefunc(" + $('#sorthist').val() + "),1,100,100),query($q))&randomParameter=" + Math.floor(Math.random() * 50000); // boost
            console.log("Using boost");
        } else if ($('input[name="radio-v-1"]:checked').val() == "sort") {
            queryString = queryString + "&sort=lirefunc(" + $('#sorthist').val() + ")+asc&randomParameter=" + Math.floor(Math.random() * 50000); // sort
            console.log("Using sort");
        } else {
            queryString = queryString + "&fq={!frange l=0 u=40 cache=false cost=100}lirefunc(" + $('#sorthist').val() + ")"; // range
            console.log("Using range");
        }

    }
    // http://localhost:9000/solr/lire/select?q=tags%3Aaustria%0A&wt=json&indent=true
    console.log(queryString);

    $.ajax(serverUrl, {
        dataType: 'jsonp',
        'jsonp': 'json.wrf',
        'wt': 'json',
        success: function (myResult) {

//    $.getJSON(queryString, function (myResult) {

            $("#perf").html("Index search time: " + myResult.responseHeader.QTime + " ms");
            $(".title").html("Results for \"" + $('#tagsearch').val() + "\"");
            console.log(myResult);

            var last = $("#results");
            wrapper = $("<div class=\"ui-grid-c\"></div>");
            wrapper.insertAfter(last);
            last = wrapper;
            for (var i = 0; i < myResult.response.docs.length; i++) {
                myID = myResult.response.docs[i].id.toString();
                imageUrl = reWriteImageUrl(myID);
                tags = myResult.response.docs[i].tags[0].toString();
                if (tags.length > 60)
                    tags = tags.substring(0, 57) + "...";
                var col = "ui-block-a";
                if (i % 4 == 1) {
                    col = "ui-block-b";
                } else if (i % 4 == 2) {
                    col = "ui-block-c";
                } else if (i % 4 == 3) {
                    col = "ui-block-d";
                }
                recent = $("<div class=\"" + col + "\"><div style=\"height:170px\"><img style=\"max-width:160px;max-height:160px;display: block;margin-left: auto;margin-right: auto;\" src=\"" + imageUrl + "\" title=\"" + myResult.response.docs[i].tags[0].toString() + "\"/></div>"
                        + getCBIRLinks(myID)
                        + "<div style=\"font-size:8pt\"><p>sort: "
                        + "<a href=\"javascript:extract('fc','" + imageUrl + "');\">fc</a> "
                        + "<a href=\"javascript:extract('fo','" + imageUrl + "');\">fo</a> "
                        + "<a href=\"javascript:extract('jh','" + imageUrl + "');\">jh</a> "
                        + "<a href=\"javascript:extract('oh','" + imageUrl + "');\">oh</a> "
                        + "<a href=\"javascript:extract('jp','" + imageUrl + "');\">jp</a> "
                        + "<a href=\"javascript:extract('cl','" + imageUrl + "');\">cl</a> "
                        + "<a href=\"javascript:extract('ce','" + imageUrl + "');\">ce</a> "
                        + "<a href=\"javascript:extract('eh','" + imageUrl + "');\">eh</a> "
                        + "<a href=\"javascript:extract('jc','" + imageUrl + "');\">jc</a> "
                        + "<a href=\"javascript:extract('ph','" + imageUrl + "');\">ph</a> "
                        + "<a href=\"javascript:extract('sc','" + imageUrl + "');\">sc</a> "
                        + "<a href=\"javascript:extract('ll','" + imageUrl + "');\">ll</a> "
                        + "<a href=\"javascript:extract('ga','" + imageUrl + "');\">ga</a> "
                        + "<a href=\"javascript:extract('ta','" + imageUrl + "');\">ta</a><br/>"
                        + "</p></div></div>");

                last.append(recent);
            }
        }
    });

}

$('#tagsearch').keypress(function (e) {
    if (e.which == 13 && $('#tagsearch').val().length >= 1) {
        tagSearchDo(); // do tag based search ...
    }
});

$(document).ready(function () {
    // get JSON-formatted data from the server
    $("#perf").html("Please stand by .... <img src=\"img/loader-light.gif\"/>");

    $.ajax("http://localhost:8983/solr/media_shard1_replica1/lireq?field=sc_ha&wt=json&url=http://192.168.1.142/images/data/digitalcandy/ml/images/Faces/image_0025.jpg", {
        dataType: 'jsonp',
        'jsonp': 'json.wrf',
        'wt': 'json',
        success: function (myResult) {

//    $.getJSON(serverUrlPrefix, function (myResult) {
            $("#perf").html("Index search time: " + myResult.responseHeader.QTime + " ms");
            console.log(myResult);
            printResults(myResult.response.docs);
        }});

});

function extract(field, url) {
    serverUrl = serverUrlPrefix + "?extract=" + url + "&field=" + field + "_ha";
    console.log(serverUrl);

    $.ajax(serverUrl, {
        dataType: 'jsonp',
        'jsonp': 'json.wrf',
        'wt': 'json',
        success: function (myResult) {

//    $.getJSON(serverUrl, function (myResult) {
            console.log(myResult);

            if (!JSON.stringify(myResult.Error)) {
                $('#sorthist').val(encodeURIComponent(field + ",\"" + myResult.histogram + "\""));
                tagSearchDo(); // do tag search ...
            }
            else {
                console.log("Error: \"" + JSON.stringify(myResult.Error) + "\"");
            }
        }
    });
}

function getRange(field) {
    var result = "40";
    if (field == "fc")
        result = "20";
    else if (field == "fo")
        result = "20";
    else if (field == "jh")
        result = "20";
    else if (field == "oh")
        result = "20";
    else if (field == "jp")
        result = "20";
    else if (field == "ce")
        result = "10";
    else if (field == "jc")
        result = "10";
    else if (field == "eh")
        result = "100";
    else if (field == "ph")
        result = "2000";
    else if (field == "sc")
        result = "150";
    return result;
}

function hashSearch(field, url) {
    serverUrl = serverUrlPrefix + "?extract=" + url + "&field=" + field + "_ha";
    console.log("Hash based search" + serverUrl);
    $(".ui-grid-c").remove();
    $("#perf").html("Please stand by .... <img src=\"img/loader-light.gif\"/>");

    $.ajax(serverUrl, {
        dataType: 'jsonp',
        'jsonp': 'json.wrf',
        'wt': 'json',
        success: function (myResult) {

//    $.getJSON(serverUrl, function (myResult) {
            console.log(myResult);
            if (!JSON.stringify(myResult.Error)) {
                var hashString = "";
                var numHashes = 35;
                if ($("#slider-1").val()) {
                    numHashes = Math.floor(myResult.hashes.length * $("#slider-1").val());
                    numHashes = Math.min(myResult.hashes.length, numHashes);
                }
                for (var i = 0; i < Math.max(5, numHashes); i++) {
                    hashString += myResult.hashes[i] + " ";
                }
                queryString = searchUrlPrefix + "?q=id:*&fq=" + field + "_ha:(" + hashString.trim() + ")&wt=json&rows=60&start=0";
                if ($('input[name="radio-v-1"]:checked').val() == "boost") {
                    queryString = queryString + "&defType=edismax&boost=div(recip(lirefunc(" + encodeURIComponent(field + ",\"" + myResult.histogram + "\"") + "),1,100,100),query($q))"; // boost
                    console.log("Using boost");
                } else if ($('input[name="radio-v-1"]:checked').val() == "sort") {
                    queryString = queryString + "&sort=lirefunc(" + encodeURIComponent(field + ",\"" + myResult.histogram + "\"") + ")+asc"; // sort
                    console.log("Using sort");
                } else {
                    queryString = queryString + "&fq={!frange l=0 u=" + getRange(field) + " cache=false cost=100}lirefunc(" + encodeURIComponent(field + ",\"" + myResult.histogram + "\"") + ")"; // range
                    console.log("Using range");
                }

                console.log(queryString);

                // now get the results:
                $.ajax(queryString, {
                    dataType: 'jsonp',
                    'jsonp': 'json.wrf',
                    'wt': 'json',
                    success: function (myResult2) {

//                $.getJSON(queryString, function (myResult2) {
                        //$("#perf").html("Search took " + myResult2.responseHeader.QTime + " ms, " + myResult2.response.numFound + " documents found");
                        console.log(myResult2);

                        if (!JSON.stringify(myResult2.Error)) {
                            printResults(myResult2.response.docs);
                        }
                        else {
                            console.log("Error: \"" + JSON.stringify(myResult2.Error) + "\"");
                        }
                    }});
            }
            else {
                console.log("Error: \"" + JSON.stringify(myResult.Error) + "\"");
            }
        }});
}

function search(idString, field) {
    // console.log(idString);
    // clear the old data
    $(".ui-grid-c").remove();
    $("#perf").html("Please stand by .... <img src=\"img/loader-light.gif\"/>");
    $(".title").html("Results for query id \"" + idString + "\"");

    // console.log($("#slider-1").val());

    // get all the new data from the server ...
    serverUrl = serverUrlPrefix + "?rows=30&id=" + idString + "&field=" + field;
    if ($("#slider-1").val())
        serverUrl += "&accuracy=" + $("#slider-1").val();
    if ($("#slider-can").val())
        serverUrl += "&candidates=" + $("#slider-can").val();
    console.log(serverUrl);

    $.ajax(serverUrl, {
        dataType: 'jsonp',
        'jsonp': 'json.wrf',
        'wt': 'json',
        success: function (myResult) {

//    $.getJSON(serverUrl, function (myResult) {
            $("#perf").html("Index search time: " + myResult.responseHeader.QTime + " ms (query " + myResult.RawDocsSearchTime + " ms, rank " + myResult.ReRankSearchTime + " ms)");
            console.log(myResult);

            if (!JSON.stringify(myResult.Error)) {
                printResults(myResult.response.docs);
            }
            else {
                console.log("Error: \"" + JSON.stringify(myResult.Error) + "\"");
            }
        }
    });
}

function searchUrl(field) {
    console.log($("#urlq").val());
    console.log(field);
    // clear the old data
    clearData();
    $(".title").html("Results for query \"" + $("#urlq").val().substring(0, 12) + "...\"");

    // get all the new data from the server ...
    serverUrl = serverUrlPrefix + "?rows=30&url=" + $("#urlq").val() + "&field=" + field;
    if ($("#slider-1").val())
        serverUrl += "&accuracy=" + $("#slider-1").val();
    if ($("#slider-can").val())
        serverUrl += "&candidates=" + $("#slider-can").val();

    console.log(serverUrl);

    $.ajax(serverUrl, {
        dataType: 'jsonp',
        'jsonp': 'json.wrf',
        'wt': 'json',
        success: function (myResult) {

//    $.getJSON(serverUrl, function (myResult) {
            $("#perf").html("Index search time: " + myResult.responseHeader.QTime + " ms (query " + myResult.RawDocsSearchTime + " ms, rank " + myResult.ReRankSearchTime + " ms)");
            console.log(myResult);
            printResults(myResult.response.docs);
        }});
}