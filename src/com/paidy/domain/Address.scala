package com.paidy.domain

case class Address(line1: String,
                   line2: String,
                   city: String,
                   state: String,
                   zip: String) {
    def hash(): String = s"$line1.$line2.$zip"
}

