const cus = {
	horizontal: true,
	direction: "ltr",
	/**
	 * Split up a text node into ranges for each character
	 * @private
	 * @param {Node} node text node
	 * @return {Range[]} array of ranges, one per character
	 */
	splitTextNodeIntoCharRanges(node){
		var ranges = [];
		var textContent = node.textContent || "";
		var textLength = textContent.length;
		var range;
		var doc = node.ownerDocument;

		if (node.nodeType != Node.TEXT_NODE || textLength === 0) {
			return ranges;
		}

		for (var i = 0; i < textLength; i++) {
			range = doc.createRange();
			range.setStart(node, i);
			range.setEnd(node, i + 1);
			ranges.push(range);
		}

		return ranges;
	},

	/**
	 * Find Text Start Range at character level precision
	 * @private
	 * @param {Node} node root node
	 * @param {number} start position to start at
	 * @param {number} end position to end at
	 * @return {Range}
	 */
	findTextStartRangeByChar(node, start, end){
		var ranges = cus.splitTextNodeIntoCharRanges(node);
		var range;
		var pos;
		var left, top, right;

		if (ranges.length === 0) {
			return null;
		}

		for (var i = 0; i < ranges.length; i++) {
			range = ranges[i];

			pos = range.getBoundingClientRect();

			if (this.horizontal && this.direction === "ltr") {

				left = pos.left;
				if( left >= start ) {
					return range;
				}

			} else if (this.horizontal && this.direction === "rtl") {

				right = pos.right;
				if( right <= end ) {
					return range;
				}

			} else {

				top = pos.top;
				if( top >= start ) {
					return range;
				}

			}

		}

		return ranges[0];
	},

	/**
	 * Find Text End Range at character level precision
	 * @private
	 * @param {Node} node root node
	 * @param {number} start position to start at
	 * @param {number} end position to end at
	 * @return {Range}
	 */
	findTextEndRangeByChar(node, start, end){
		var ranges = cus.splitTextNodeIntoCharRanges(node);
		var prev;
		var range;
		var pos;
		var left, right, top, bottom;

		if (ranges.length === 0) {
			return null;
		}

		for (var i = 0; i < ranges.length; i++) {
			range = ranges[i];

			pos = range.getBoundingClientRect();

			if (this.horizontal && this.direction === "ltr") {

				left = pos.left;
				right = pos.right;

				if(left > end && prev) {
					return prev;
				} else if(right > end) {
					return range;
				}

			} else if (this.horizontal && this.direction === "rtl") {

				left = pos.left
				right = pos.right;

				if(right < start && prev) {
					return prev;
				} else if(left < start) {
					return range;
				}

			} else {

				top = pos.top;
				bottom = pos.bottom;

				if(top > end && prev) {
					return prev;
				} else if(bottom > end) {
					return range;
				}

			}


			prev = range;

		}

		return ranges[ranges.length-1];

	}
};