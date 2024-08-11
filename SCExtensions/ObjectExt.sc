+Object {
    *+ { |factor, other|
        ^this * factor + other
    }

	custom { |func|
        ^func.value(this)
    }

	customCollect { |func|
        ^func.value(this) // Apply the function to any object
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
}