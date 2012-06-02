/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/********** Scales **********/

if(!LABKEY.vis.Scale){
	LABKEY.vis.Scale = {};
}

LABKEY.vis.Scale.Discrete = function(domain, range){
	// This is a discrete scale, used for categorical data (e.g. visits).
	return d3.scale.ordinal().domain(domain).rangeBands(range, 1);
};

LABKEY.vis.Scale.Continuous = function(trans, data, value, domain, range){
	// This is a continuous scale (e.g. dates, numbers).
	var scale = null;

	if(!domain){
		var max = d3.max(data, value);
		var min = d3.min(data, value);
		domain = [min, max];
	}
	
	if(trans == 'linear'){
		scale = d3.scale.linear().domain(domain).range(range);
        return scale;
	} else {
		scale = d3.scale.log().domain(domain).range(range);
        var logScale = function(val){
            return val <= 0 ? (scale(scale.domain()[0]) - 5) : scale(val);
        };
        logScale.domain = scale.domain;
        logScale.range = scale.range;
//        logScale.ticks = function(){
//            var ticks = [];
//            // Rounding because there is a weird issue where d3 log scales warp the domain
//            // ex:  s = d3.scale.log().domain([5, 150]).range([200, 500]);
//            //      s.domain() = [4.99999999999999999, 150]
//            var i = Math.round(logScale.domain()[0]);
//            while(i < logScale.domain()[1]){
//                ticks.push(i);
//                i = i * 10;
//            }
//            return ticks;
//        };
        logScale.ticks = scale.ticks;

        return logScale;
	}
};

LABKEY.vis.Scale.ColorDiscrete = function(){
	// Used for discrete color scales (color assigned to categorical data)
    return d3.scale.ordinal().range([ "#66C2A5", "#FC8D62", "#8DA0CB", "#E78AC3", "#A6D854", "#FFD92F", "#E5C494", "#B3B3B3"]);
};

LABKEY.vis.Scale.DarkColorDiscrete = function(){
	// Used for discrete color scales (color assigned to categorical data)
    return d3.scale.ordinal().range(["#378a70", "#f34704", "#4b67a6", "#d53597", "#72a124", "#c8a300", "#d19641", "#808080"]);
};

LABKEY.vis.Scale.Shape = function(){
    // Used in pointType geom.
    var circle = function(paper, x, y, r){ return paper.circle(x, y, r)};
    var square = function(paper, x, y, r){ return paper.rect(x-r, y-r, r*2, r*2)};
    var diamond = function(paper, x, y, r){r = (Math.sqrt(2*Math.pow(r*2, 2)))/2; return paper.path('M' + x + ' ' + (y+r) + ' L ' + (x+r) + ' ' + y + ' L ' + x + ' ' + (y-r) + ' L ' + (x-r) + ' ' + y + ' Z')};
    var triangle = function(paper, x, y, r){return paper.path('M ' + x + ' ' + (y + (r)) + ' L ' + (x + (r)) + ' ' + (y-(r)) + ' L ' + (x - (r)) + ' ' + (y - (r)) + ' Z')};
    var x = function(paper, x, y, r){ return paper.path('M' + (x-r) + ' ' + (y+r) + ' L '  + (x+r) + ' ' + (y-r) + 'M' + (x-r) + ' ' + (y-r) + ' L '  + (x+r) + ' ' + (y+r)).attr('stroke-width', 3)};
    
    return d3.scale.ordinal().range([circle, triangle, square, diamond, x]);
};
