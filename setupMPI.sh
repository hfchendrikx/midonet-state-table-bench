#!/bin/bash

BASE_DIR=/Users/Nicolas/IdeaProjects/mido-bench
OMPI_DESTDIR=${BASE_DIR}/openmpi/build/install
OMPI_PREFIX="/opt/openMPI"

export OPAL_DESTDIR="$OMPI_DESTDIR"
export OMPI_PREFIX="$OMPI_PREFIX"
