# -*- shell-script -*-
#
# Copyright (c) 2004-2005 The Trustees of Indiana University and Indiana
#                         University Research and Technology
#                         Corporation.  All rights reserved.
# Copyright (c) 2004-2005 The University of Tennessee and The University
#                         of Tennessee Research Foundation.  All rights
#                         reserved.
# Copyright (c) 2004-2005 High Performance Computing Center Stuttgart, 
#                         University of Stuttgart.  All rights reserved.
# Copyright (c) 2004-2005 The Regents of the University of California.
#                         All rights reserved.
# Copyright (c) 2010      Cisco Systems, Inc.  All rights reserved.
# Copyright (c) 2008-2014 University of Houston. All rights reserved.
# $COPYRIGHT$
# 
# Additional copyrights may follow
# 
# $HEADER$
#


# MCA_fs_plfs_CONFIG(action-if-can-compile, 
#                        [action-if-cant-compile])
# ------------------------------------------------
AC_DEFUN([MCA_ompi_fs_plfs_CONFIG],[
    AC_CONFIG_FILES([ompi/mca/fs/plfs/Makefile])

    OMPI_CHECK_PLFS([fs_plfs],
                     [fs_plfs_happy="yes"],
                     [fs_plfs_happy="no"])

    AS_IF([test "$fs_plfs_happy" = "yes"],
          [$1],
          [$2])

    # substitute in the things needed to build plfs
    AC_SUBST([fs_plfs_CPPFLAGS])
    AC_SUBST([fs_plfs_LDFLAGS])
    AC_SUBST([fs_plfs_LIBS])
])dnl
