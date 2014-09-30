#!/usr/bin/perl

=head1 NAME

proto2csv.pl - convert a benchmark protocol buffer into a cvs line

=head1 SYNOPSIS

proto2csv.pl [-o output_csv] [-s separator] [-e extra_info] protobuf_file ...

=head1 DESCRIPTION

Generates a CSV file out of a series of files containing protocol
buffer data for a benchmark. The output file can be specified using
"-o" (it goes to stdout by default). It is possible to add additional
information to identify the experiment using the "-e" option.

By default, the separator is a semicolon. Note that no escaping is done
to prevent data values containing the separator...

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
use FindBin qw($Script $RealBin);

my $SEPARATOR = ";";
my $HELP = 0;
my $OUTPUTFILE = undef;
my $EXTRA = "";

sub usage() {
    print "$Script protobuf_file ...\n";
    exit(1);
}

GetOptions(
    'help|h' => \$HELP,
    'output|o=s' => \$OUTPUTFILE,
    'separator|s=s' => \$EXTRA,
    'extra|e=s' => \$EXTRA,
);
usage if ($HELP);

my $OUTPUT;
if (defined($OUTPUTFILE)) {
    open($OUTPUT, ">$OUTPUTFILE") ||
        die "cannot open output file: $OUTPUTFILE\n";
} else {
    $OUTPUT = \*STDOUT if (!defined($OUTPUT));
}

my $PROTOSPECDIR = "$RealBin/../src/main/proto";
my $PROTOSPEC = "$PROTOSPECDIR/benchmarks.proto";
die "cannot find benchmark protobuf spec\n" if (! -f $PROTOSPEC);


my %PROTO_OPTS;
$PROTO_OPTS{include_dir} = [$PROTOSPECDIR];
$PROTO_OPTS{create_accessors} = 1;
$PROTO_OPTS{follow_best_practice} = 1;

Google::ProtocolBuffers->parsefile($PROTOSPEC, \%PROTO_OPTS);

sub get_data($$) {
    my $obj = $_[0];
    my @path = split(/\./, $_[1]);
    while ($#path >= 0) {
        $obj = $obj->{shift(@path)};
    }
    if (!defined($obj)) {
        return "";
    }
    return $obj;
}

my @MAIN_HEADERS = (
    [name => "name"],
    [tstamp => "tstamp"],
    [host => "host"],
    [testbed => "testbed"],
    [tag => "tag"],
);

my @PARAM_HEADERS = (
    ["topology.num_tenants" => "ntenants"],
    ["topology.bridges_per_tenant" => "bridges_per_tenant"],
    ["topology.ports_per_bridge" => "ports_per_bridge"],
    [topology_shuffle => "shuffle"],
    [notification_count => "notification_count"],
    [repeat => "repeat"],
    ["env.zk_info.zk_instances" => "zk_instances"],
    ["env.zk_info.zk_hosts" => "zk_hosts"],
    ["mpi.size" => "mpi_procs"],
    ["mpi.node_count" => "mpi_nodes"],
    ["mpi.host_list" => "mpi_hosts"],
);

my @DATA_HEADERS = (
    [name => "value_name"],
    [tag => "value_tag"],
    [ivalue => "ivalue"],
    [fvalue => "fvalue"],
    [svalue => "svalue"],
    [tstamp => "value_tstamp"],
);



my $HEADER_LINE = join($SEPARATOR,
    (map {$_->[1]} (@MAIN_HEADERS, @PARAM_HEADERS)), "extra",
    (map {$_->[1]} (@DATA_HEADERS)));
print $OUTPUT $HEADER_LINE, "\n";

foreach my $infile (@ARGV) {
    my $pb;
    my $in;
    if (!open($in, "<$infile")) {
        warn "cannot open encoded protocol buffer file: $infile\n";
        next;
    }
    binmode($in);
    {local $/; $pb = Org::Midonet::Benchmarks::Benchmark->decode(<$in>);}
    close($in);

    my @common = ();
    foreach my $f (@MAIN_HEADERS, @PARAM_HEADERS) {
        push(@common, get_data($pb, $f->[0]));
    }
    push(@common, $EXTRA);
    my $desc = join($SEPARATOR, @common);

    my @data = @{$pb->{data}};
    foreach my $d (@data) {
        my @values = ();
        foreach my $f (@DATA_HEADERS) {
            push(@values, get_data($d, $f->[0]));
        }
        my $dataline = join($SEPARATOR, $desc, @values);
        print $OUTPUT $dataline, "\n";
    }
}

