# Midonet scalability benchmarks

## For the impatient

This repository contains benchmarking code aimed to evaluate the scalability
of Replicated Maps and Sets used by Midonet. These data structures are used,
 among others, to implement MAC, ARP, and routing tables.

It depends on the actual Midonet code, so the following previous steps are
required:

* When assembling the benchmarks with gradle, set the midonetPath property to
  point to where you cloned this repository. Then execute:

    gradle wrapper
    ./gradlew clean assemble

## Contents

* openmpi: a snapshot of the development branch of project openMPI with
  java support. This provides a framework for synchronized process execution
  across several machines. Note that this package provides not only a jar, but
  also native libraries which must be correctly installed

* mpitest: a simple MPI test application to test the MPI framework

* benchmarks: Midonet Replicated Maps and Sets benchmarks


