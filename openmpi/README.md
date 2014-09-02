Description
===========

A snapshot of the Open MPI (www.open-mpi.org) development branch
containing java support.

A quote from the Open MPI distribution:

> IMPORTANT NOTE
> 
> JAVA BINDINGS ARE PROVIDED ON A "PROVISIONAL" BASIS - I.E., THEY ARE
> NOT PART OF THE CURRENT OR PROPOSED MPI STANDARDS. THUS, INCLUSION OF
> JAVA SUPPORT IS NOT REQUIRED BY THE STANDARD. CONTINUED INCLUSION OF
> THE JAVA BINDINGS IS CONTINGENT UPON ACTIVE USER INTEREST AND
> CONTINUED DEVELOPER SUPPORT.


Sources
=======

The sources contained in this project have been obtained from the official
Open MPI subversion repository (read-only):

> $ svn co http://svn.open-mpi.org/svn/ompi/trunk ompi-trunk

Note that java support is only present in the development branches.


Directories and files
=====================

* _[ompi-trunk]_ : A copy of the the Open MPI repository
  (development trunk branch), plus local configuration scripts
* _[build/ompi-version]_ : full version of the current Open MPI version
* _[build/ompi-build]_ : build directory
* _[build/install]_ : local deployment of the Open MPI binaries


Building
========

You may use the _build_ task in the graddle build script. It will generate
the binaries in _./build/install/opt/openMPI_.

> $ gradle build


Installing
==========

The gradle build script configures Open MPI to be installed in _/opt/openMPI_.
So, installation consists in copying the contents in
_./build/install/opt/openMPI_ into _/opt/openMPI_. Before that, you should
remove any previously installed version.

Alternatively, using a console, you can go to _./build/ompi-build_ and execute:

> $ sudo make install

An automated installation method (e.g. via debian package) is in the TO-DO list.


Running the applications
========================

MPI applications are launched via the mpirun program. This has 2 requirements:

* _/opt/openMPI/bin_ must be in the PATH
* _/opt/openMPI/lib_ must be in the library path (either by setting
  *LD_LIBRARY_PATH* or by adding the path in a file under _/etc/ld.so.conf.d/_.

Note that Open MPI must be installed (and the commands path and library path
correctly set) in all the systems were the application will run.

In order to launch a java application, you may use the following syntax
(note the different number of dashes (-) in the options):

> mpirun --bind-to none -prefix /etc/openMPI -np 2 java JavaArgs JavaApp

The line above will execute two instances of JavaApp in the local machine.
In order to run them in different hosts, the following can be used:

> mpirun --bind-to none -prefix /etc/openMPI -host host1,host2 java JavaArgs JavaApp


