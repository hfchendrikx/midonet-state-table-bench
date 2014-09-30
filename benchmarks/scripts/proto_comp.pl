#!/usr/bin/perl

=head1 NAME

proto_comp.pl - compile protocol buffer descriptions into a perl module (pm)

=head1 SYNOPSIS

proto_comp.pl [-I include_dir:...] [-o output.pm] file.proto

=head1 DESCRIPTION

Compiles proto message specifications into a perl module, which can be
imported via 'use' from a perl script (see Google::ProtocolBuffers for
additional details). If the output file is not specified with '-o', the
standard output is used. One or more inlude files, indicating where to
look for additional protocol buffer specifications to import, can be
provided using '-I' (either repeating the -I option, or separating the
directories via colon (:))

=head1 REQUIREMENTS

=over

=item Google::ProtocolBuffers

L<http://search.cpan.org/perldoc?Google%3A%3AProtocolBuffers>

=back

=cut

use strict;
use warnings;

use Google::ProtocolBuffers;
use Getopt::Long;
use Cwd qw(abs_path);
use FindBin qw($Script);

my $HELP = 0;
my $INCLUDES = [];
my $OUTPUT = undef;

sub usage() {
    print "$Script [-I include_dir:...] [-o output.pm] file.proto\n";
    exit(1);
}

GetOptions(
    'help|h' => \$HELP,
    'include|I=s@' => \$INCLUDES,
    'output|o=s' => \$OUTPUT,
);

$OUTPUT = \*STDOUT if (!defined($OUTPUT));

my @INCLUDEDIRS = split(/:/, join(':', @$INCLUDES));
usage() if ($HELP);

my $PROTOSPEC = shift(@ARGV);
usage() if (!defined($PROTOSPEC));


my %PROTO_OPTS;
$PROTO_OPTS{include_dir} = \@INCLUDEDIRS;
$PROTO_OPTS{generate_code} = $OUTPUT;
$PROTO_OPTS{create_accessors} = 1;
$PROTO_OPTS{follow_best_practice} = 1;

Google::ProtocolBuffers->parsefile($PROTOSPEC, \%PROTO_OPTS);

