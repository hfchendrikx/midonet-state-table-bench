# Midonet scalability benchmarks

## For the impatient

This repository contains benchmarking code aimed to evaluate the scalability
of different Midonet components, starting with the topology support.

It depends on the actual Midonet code, so the following previous steps are
required:

* Download compile and locally install Midonet

    git clone https://github.com/midonet/midonet
    cd midonet
    gradle wrapper
    ./gradlew clean assemble installApp
    
* When assembling the benchmarks with gradle, set the midonetPath property to
  point to the midonet project directory (you may create a gradle.properties
  file to set that value). Then, you can execute

    gradle wrapper
    ./gradlew clean assemble

## Contents

* openmpi: a snapshot of the development branch of project openMPI with
  java support. This provides a framework for synchronized process execution
  across several machines. Note that this package provides not only a jar, but
  also native libraries which must be correctly installed

* mpitest: a simple MPI test application to test the MPI framework

* benchmarks: Midonet topology benchmarks


