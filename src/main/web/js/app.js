function reWriteImageUrl(imgUrlOriginal) {
    return "http://localhost/images" + imgUrlOriginal;
}

function getCBIRLinks(myID) {
    result = "";
    result += "lr: ";
    result += "<a href=\"javascript:search('" + myID + "', 'cl_ha');\">cl</a>, ";
    result += "<a href=\"javascript:search('" + myID + "', 'ce_ha');\">ce</a>, ";
    result += "<a href=\"javascript:search('" + myID + "', 'eh_ha');\">eh</a>, ";
    result += "<a href=\"javascript:search('" + myID + "', 'jc_ha');\">jc</a>, ";
    result += "<a href=\"javascript:search('" + myID + "', 'ph_ha');\">ph</a>, ";
    result += "<a href=\"javascript:search('" + myID + "', 'sc_ha');\">sc</a>, ";
    result += "<a href=\"javascript:search('" + myID + "', 'fo_ha');\">fo</a>, ";
    result += "<a href=\"javascript:search('" + myID + "', 'fc_ha');\">fc</a>, ";
    result += "<a href=\"javascript:search('" + myID + "', 'jh_ha');\">jh</a>";
    result += "<br>sr: ";
    result += "<a href=\"javascript:hashSearch('cl','" + imageUrl + "');\">cl</a>, "
    result += "<a href=\"javascript:hashSearch('ce','" + imageUrl + "');\">ce</a>, "
    result += "<a href=\"javascript:hashSearch('eh','" + imageUrl + "');\">eh</a>, "
    result += "<a href=\"javascript:hashSearch('jc','" + imageUrl + "');\">jc</a>, "
    result += "<a href=\"javascript:hashSearch('ph','" + imageUrl + "');\">ph</a>, "
    result += "<a href=\"javascript:hashSearch('sc','" + imageUrl + "');\">sc</a>, "
    result += "<a href=\"javascript:hashSearch('fo','" + imageUrl + "');\">fo</a>, "
    result += "<a href=\"javascript:hashSearch('fc','" + imageUrl + "');\">fc</a>, "
    result += "<a href=\"javascript:hashSearch('jh','" + imageUrl + "');\">jh</a>"

    return result; // + "</br>";
}

function tagSearchDo() {
    console.log($('#tagsearch').val());
    // clear the old data
    $(".imgCont").remove();
    $(".imgContLarge").remove();
    $("#perf").html("Please stand by .... <img src=\"img/loader-light.gif\"/>");
    queryString = "http://localhost:8983/solr/media_shard1_replica1/select?q=" + $('#tagsearch').val() + "&rows=60";
    console.log($('input[name="radio-v-1"]:checked').val());
    if ($('#sorthist').val()) {
        if ($('input[name="radio-v-1"]:checked').val() === "boost") {
            queryString = queryString + "&defType=edismax&boost=div(recip(lirefunc(" + $('#sorthist').val() + "),1,100,100),query($q))&randomParameter=" + Math.floor(Math.random() * 50000); // boost
            console.log("Using boost");
        } else if ($('input[name="radio-v-1"]:checked').val() === "sort") {
            queryString = queryString + "&sort=lirefunc(" + $('#sorthist').val() + ")+asc&randomParameter=" + Math.floor(Math.random() * 50000); // sort
            console.log("Using sort");
        } else {
            queryString = queryString + "&fq={!frange l=0 u=40 cache=false cost=100}lirefunc(" + $('#sorthist').val() + ")"; // range
            console.log("Using range");
        }

    }

    $.ajax(queryString, {
        dataType: 'jsonp',
        'jsonp': 'json.wrf',
        'wt': 'json',
        success: function (myResult) {
            $("#perf").html("Index search time: " + myResult.responseHeader.QTime + " ms");
            $("#results").html("Results for \"" + $('#tagsearch').val() + "\"");
            console.log(myResult);
            var last = $("#results");
            for (var i = 0; i < myResult.response.docs.length; i++) {
                myID = myResult.response.docs[i].id.toString();
                imageUrl = encodeURIComponent(reWriteImageUrl(myID));
                tags = myResult.response.docs[i].tags[0].toString();
                if (tags.length > 60)
                    tags = tags.substring(0, 57) + "...";
                recent = $("<div class=\"imgContLarge\"><img class=\"lireimg\" src=\"" + imageUrl + "\" title=\"" + myResult.response.docs[i].tags[0].toString() + "\"/>"
                        + "<div align=\"center\" class=\"searchLink\">"
                         + "d="+myResult.response.docs[i].distance+"<br/>"
                        + "<a href=\"javascript:search('" + myID + "', 'oh_ha');\">OH</a>, "
                        + "CBIR: " + getCBIRLinks(myID)
                        + "<br/>sort: "
                        + "<a href=\"javascript:extract('cl','" + imageUrl + "');\">cl</a>, "
                        + "<a href=\"javascript:extract('ce','" + imageUrl + "');\">ce</a>, "
                        + "<a href=\"javascript:extract('eh','" + imageUrl + "');\">eh</a>, "
                        + "<a href=\"javascript:extract('jc','" + imageUrl + "');\">jc</a>, "
                        + "<a href=\"javascript:extract('ph','" + imageUrl + "');\">ph</a>, "
                        + "<a href=\"javascript:extract('fo','" + imageUrl + "');\">fo</a>, "
                        + "<a href=\"javascript:extract('fc','" + imageUrl + "');\">fc</a>, "
                        + "<a href=\"javascript:extract('jh','" + imageUrl + "');\">jh</a>, "
                        + "<a href=\"javascript:extract('sc','" + imageUrl + "');\">sc</a><br/>"
                        + "</div></div>");
                recent.insertAfter(last);
                last = recent;
            }

        },
        error: function (error, data, type) {
            console.error(error);
        }
    });

}

$('#tagsearch').keypress(function (e) {
    if (e.which === 13 && $('#tagsearch').val().length >= 1) {
        tagSearchDo(); // do tag based search ...
    }
});

$(document).ready(function () {
    // get JSON-formatted data from the server
    $("#perf").html("Please stand by .... <img src=\"img/loader-light.gif\"/>");

    $.ajax("http://localhost:8983/solr/media_shard1_replica1/lireq?field=sc_ha&url=http://localhost/images/data/digitalcandy/ml/images/20.jpg", {
        dataType: 'jsonp',
        'jsonp': 'json.wrf',
        'wt': 'json',
        success: function (myResult) {
            $("#perf").html("Index search time: " + myResult.responseHeader.QTime + " ms");
            console.log(myResult);
            var last = $("#results");
            for (var i = 0; i < myResult.response.docs.length; i++) {
                myID = myResult.response.docs[i].id.toString();
                imageUrl = reWriteImageUrl(myID);
                recent = $("<div class=\"imgCont\"><img class=\"lireimg\" src=\"" + imageUrl + "\" />"
                        + "<div align=\"center\" class=\"searchLink\">"
                        + getCBIRLinks(myID)
                        // + "<a href=\"javascript:extract('http://localhost:9000/solr/images/"+myID+"');\">Extract</a>, "
                        + "</div></div>");
                recent.insertAfter(last);
                last = recent;
            }
        },
        error: function (error, data, type) {
            console.error(error);
        }});
});

function extract(field, url) {
    serverUrl = "http://localhost:8983/solr/media_shard1_replica1/lireq?extract=" + url + "&field=" + field + "_ha";
    console.log(serverUrl);
    $.ajax(serverUrl, {
        dataType: 'jsonp',
        'jsonp': 'json.wrf',
        'wt': 'json',
        success: function (myResult) {
            $('#sorthist').val(encodeURIComponent(field + ",\"" + myResult.response.histogram + "\""));
            tagSearchDo(); // do tag search ...
        },
        error: function (error, data, type) {
            console.error(error);

        }
    });

}


function getRange(field) {
    var result = "40";
    if (field === "cl")
        result = "20";
    else if (field === "ce")
        result = "10";
    else if (field === "jc")
        result = "10";
    else if (field === "eh")
        result = "100";
    else if (field === "ph")
        result = "200";
    else if (field === "sc")
        result = "150";
    else if (field === "fc")
        result = "50";
    else if (field === "fo")
        result = "50";
    else if (field === "jh")
        result = "150";
    else if (field === "oh")
        result = "50";
    return result;
}

function hashSearch(field, url) {
    serverUrl = "http://localhost:8983/solr/media_shard1_replica1/lireq?extract=" + url + "&field=" + field + "_ha";
    console.log("Hash based search" + serverUrl);
    $(".imgCont").remove();
    $(".imgContLarge").remove();
    $("#perf").html("Please stand by .... <img src=\"img/loader-light.gif\"/>");


    $.ajax(serverUrl, {
        dataType: 'jsonp',
        'jsonp': 'json.wrf',
        'wt': 'json',
        success: function (myResult) {
            console.log(myResult);
            if (myResult) {
                var hashString = "";
                var numHashes = 35;
                if ($("#slider-1").val()) {
                    numHashes = Math.floor(myResult.response.hashes.length * $("#slider-1").val());
                    numHashes = Math.min(myResult.response.hashes.length, numHashes);
                }
                for (var i = 0; i < Math.max(5, numHashes); i++) {
                    hashString += myResult.response.hashes[i] + " ";
                }
                queryString = "http://localhost:8983/solr/media_shard1_replica1/select?wt=json&q=id:*&fq=" + field + "_ha:(" + hashString.trim() + ")&rows=60&fl=*,score";
                if ($('input[name="radio-v-1"]:checked').val() === "boost") {
                    queryString = queryString + "&defType=edismax&boost=div(recip(lirefunc(" + encodeURIComponent(field + ",\"" + myResult.response.histogram + "\"") + "),1,100,100),query($q))"; // boost
                    console.log("Using boost");
                } else if ($('input[name="radio-v-1"]:checked').val() === "sort") {
                    queryString = queryString + "&sort=lirefunc(" + encodeURIComponent(field + ",\"" + myResult.response.histogram + "\"") + ")+asc"; // sort
                    console.log("Using sort");
                } else {
                    queryString = queryString + "&fq={!frange l=0 u=" + getRange(field) + " cache=false cost=100}lirefunc(" + encodeURIComponent(field + ",\"" + myResult.response.histogram + "\"") + ")"; // range
                    console.log("Using range");
                }

                console.log(queryString);

                $.ajax(queryString, {
                    dataType: 'jsonp',
                    'jsonp': 'json.wrf',
                    'wt': 'json',
                    success: function (myResult2) {
                        $("#perf").html("Search took " + myResult2.responseHeader.QTime + " ms, " + myResult2.response.numFound + " documents found");
                        console.log(myResult2);

                        if (!myResult2.Error) {
                            var last = $("#results");
                            for (var i = 0; i < myResult2.response.docs.length; i++) {
                                myID = myResult2.response.docs[i].id.toString();
                                imageUrl = reWriteImageUrl(myID);
                                recent = $("<div class=\"imgCont\"><img class=\"lireimg\" src=\"" + imageUrl + "\" />"
                                        + "<div class=\"searchLink\">"
                                         + "score="+myResult2.response.docs[i].distance+"<br/>"
                                        + getCBIRLinks(myID)
                                        + "</div></div>");
                                recent.insertAfter(last);
                                last = recent;
                            }
                        }
                        else {
                            $("#results").html("Error: \"" + myResult2.Error + "\"");
                        }

                    },
                    error: function (error, data, type) {
                        console.error(error);
                    }
                });
            }
            else {
                $("#results").html("Error: \"" + myResult.Error + "\"");
            }

        },
        error: function (error, data, type) {
            console.error(error);
            $("#results").html("Error: \"" + error + "\"");
        }
    });

}

function search(idString, field) {
    $(".imgCont").remove();
    $(".imgContLarge").remove();
    $("#perf").html("Please stand by .... <img src=\"img/loader-light.gif\"/>");
    $("#results").html("Results for query id \"" + idString + "\"");
    serverUrl = "http://localhost:8983/solr/media_shard1_replica1/lireq?rows=30&id=" + idString + "&field=" + field;
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
            $("#perf").html("Index search time: " + myResult.responseHeader.QTime + " ms (query " + myResult.RawDocsSearchTime + " ms, rank " + myResult.ReRankSearchTime + " ms)");
            console.log(myResult);
            
            var last = $("#results");
            for (var i = 0; i < myResult.response.docs.length; i++) {
                myID = myResult.response.docs[i].id.toString();
                
                imageUrl = reWriteImageUrl(myID);
                recent = $("<div class=\"imgCont\"><img class=\"lireimg\" src=\"" + imageUrl + "\" />"
                        + "<div class=\"searchLink\">"
                        + "score=" + myResult.response.docs[i].distance + "<br/>"
                        + getCBIRLinks(myID)
                        + "</div></div>");
                recent.insertAfter(last);
                last = recent;
            }
        },
        error: function (error, data, type) {
            console.error(error);
        }
    });
}

function searchUrl(field) {
    console.log($("#urlq").val());
    console.log(field);
    // clear the old data
    $(".imgCont").remove();
    $(".imgContLarge").remove();
    $("#perf").html("Please stand by .... <img src=\"img/loader-light.gif\"/>");
    $("#results").html("Results for query \"" + $("#urlq").val().substring(0, 12) + "...\"");
    // get all the new data from the server ...
    serverUrl = "http://localhost:8983/solr/media_shard1_replica1/lireq?rows=30&url=" + $("#urlq").val() + "&field=" + field;
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
            $("#perf").html("Index search time: " + myResult.responseHeader.QTime + " ms (query " + myResult.RawDocsSearchTime + " ms, rank " + myResult.ReRankSearchTime + " ms)");
            console.log(myResult);
            var last = $("#results");
            for (var i = 0; i < myResult.response.docs.length; i++) {
                myID = myResult.response.docs[i].id.toString();
                console.log(myResult.response.docs[i]);
                imageUrl = reWriteImageUrl(myID);
                recent = $("<div class=\"imgCont\"><img class=\"lireimg\" src=\"" + imageUrl + "\" />"
                        + "<div class=\"searchLink\">"
                        + "score=" + myResult.response.docs[i].distance + "<br/>"
                        + getCBIRLinks(myID)
                        + "</div></div>");
                recent.insertAfter(last);
                last = recent;
            }

        },
        error: function (error, data, type) {
            console.error(error);
        }
    });

}