+Object {
	*+ { |factor, other|
		^this * factor + other
	}

	custom { |func|
		^func.value(this)
	}

	custom1 { |func, arg1|
		^func.value(this, arg1)
	}

	custom2 { |func, arg1, arg2|
		^func.value(this, arg1, arg2)
	}

	customCollect { |func|
		^func.value(this) // Apply the function to any object
	}

	customCollect1 { |func, arg1|
		^func.value(this, arg1)
	}

	customCollect2 { |func, arg1, arg2|
		^func.value(this, arg1, arg2)
	}

}

+SimpleNumber {
	syracuse { |other|
		^if(this%2 == 0){
			this/2
		}{
			(other[0]*this) + other[1]
		}
	}
}

+Collection {
	customCollect { |func|
		^this.collect { |item| func.value(item) } // Apply the function to each element of the collection
	}

	customCollect1 { |func, arg1|
		^this.collect { |item, i| func.value(item, arg1[i]) }
	}

	customCollect2 { |func, arg1, arg2|
		^this.collect { |item, i| func.value(item, arg1[i], arg2[i]) }
	}
}
