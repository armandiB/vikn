+SimpleNumber {
		asTimeString { |precision = 0.001, maxDays = 365, dropDaysIfPossible = true, dropHoursIfPossible = true|
		var number, decimal, days, hours, minutes, seconds, mseconds, isNegative;

		// min value of precision is 0.001; this ensures that we stick to 3 decimal places in the
		// formatted string.
		precision = max(precision, 0.001);

		number = this.round(precision);
		isNegative = number < 0;
		number = number.abs;
		decimal = number.asInteger;
		days = decimal.div(86400).min(maxDays);
		days = if(dropDaysIfPossible and: { days == 0 }) {
			days = ""
		} {
			days.asString.padLeft(3, "0").add($:);
		};
		if(isNegative) {days = "-" ++ days};
		hours = decimal.div(3600) % 24;
		hours = if(dropHoursIfPossible and: { hours == 0 }) {
			hours = ""
		} {
			hours.asString.padLeft(2, "0").add($:);
		};
		minutes = (decimal.div(60) % 60).asString.padLeft(2, "0").add($:);
		seconds = (decimal % 60).asString.padLeft(2, "0");
		if(precision.frac == 0){
			mseconds = "";
		} {
			seconds = seconds.add($.);
			mseconds = number.frac * 1000;
			mseconds = mseconds.round.asInteger.asString.padLeft(3, "0");
		}
		^days ++ hours ++ minutes ++ seconds ++ mseconds
	}
}
