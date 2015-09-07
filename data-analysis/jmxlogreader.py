import matplotlib.pyplot as plt
from matplotlib.ticker import MaxNLocator

##############################
#JMX LOGS ARE IN MILLISECONDS#
##############################

LOG_TYPE_MEMORY = "mem"
LOG_TYPE_CPU = "cpu"
LOG_TYPE_BYTES_IN_PER_SECOND = "bips"
LOG_TYPE_BYTES_OUT_PER_SECOND = "bops"
LOG_TYPE_MESSAGES_IN_PER_SECOND = "mips"
LOG_TYPE_GENERAL = "general"
LOG_TYPE_GARBAGE_COLLECTION = "gc"

CLUSTER_NODE_1 = "c1"
CLUSTER_NODE_2 = "c2"
CLUSTER_NODE_3 = "c3"

LOG_JVM_KAFKA = "kafka"
LOG_JVM_ZOOKEEPER = "zoo"

LOG_JVM_ZK_MAXREQUESTLATENCY = "MaxRequestLatency"
LOG_JVM_ZK_PACKETSRECEIVED = "PacketsReceived"
LOG_JVM_ZK_PACKETSSENT = "PacketsSent"

LOG_JVM_MEM_HEAP_USED = "HeapMemoryUsage_used"
LOG_JVM_MEM_HEAP_INIT = "HeapMemoryUsage_init"
LOG_JVM_MEM_HEAP_MAX = "HeapMemoryUsage_max"
LOG_JVM_MEM_HEAP_COMMITTED = "HeapMemoryUsage_committed"

LOG_JVM_CPU_SYSTEM_LOAD_AVERAGE = "SystemLoadAverage"
LOG_JVM_CPU_SYSTEM_CPU_LOAD = "SystemCpuLoad"
LOG_JVM_CPU_PROCESS_CPU_LOAD = "ProcessCpuLoad"

LOG_JVM_GC_PARNEW_COUNT = "ParNew.CollectionCount"
LOG_JVM_GC_PARNEW_TIME = "ParNew.CollectionTime"
LOG_JVM_GC_CONCURRENTMARKSWEEP_COUNT = "ConcurrentMarkSweep.CollectionCount"
LOG_JVM_GC_CONCURRENTMARKSWEEP_TIME = "ConcurrentMarkSweep.CollectionTime"

def getFilename(name, cluster, type):
    return name + "-" + cluster + "-" + type + ".log"

def readKeyLog(filename, keys=[], start = 0, name_length=1):
    data = {}

    with open(filename) as f:
        content = f.readlines()

        for line in content:
            parts = line.strip("\n").split("\t")
            keyParts = parts[0].split(".")
            keyParts = keyParts[-name_length:]
            key = '.'.join(keyParts)

            if len(keys) == 0 or key in keys:
                if (key not in data):
                    data[key] = []

                data[key].append((long(parts[2]), float(parts[1])))

    return data

def plotHeapUsage(data, node_name="", used=False, init=False, max=False, committed=False, x0=None):
    if x0 is None:
        x0 = data[LOG_JVM_MEM_HEAP_USED][0][0];
    x = [(x[0]- x0) / (1000) for x in data[LOG_JVM_MEM_HEAP_USED]]

    if used:
        heap_used = [y[1] / (1024*1024) for y in data[LOG_JVM_MEM_HEAP_USED]]
        plt.plot(x, heap_used, label=node_name + " Heap Used", marker="o", linestyle="--")

    if init:
        heap_init = [y[1] / (1024*1024) for y in data[LOG_JVM_MEM_HEAP_INIT]]
        plt.plot(x, heap_init, label=node_name + " Heap Init")

    if max:
        heap_max = [y[1] / (1024*1024) for y in data[LOG_JVM_MEM_HEAP_MAX]]
        plt.plot(x, heap_max, label=node_name + " Heap Max")

    if committed:
        heap_committed = [y[1] / (1024*1024) for y in data[LOG_JVM_MEM_HEAP_COMMITTED]]
        plt.plot(x, heap_committed, label=node_name + " Heap Committed")

def plotCpuUsage(data, node_name="", x0=None):
    if x0 is None:
        x0 = data[LOG_JVM_CPU_PROCESS_CPU_LOAD][0][0];
    x = [(x[0]- x0) / (1000) for x in data[LOG_JVM_CPU_PROCESS_CPU_LOAD]]

    process_cpu_load = [y[1] * 100 for y in data[LOG_JVM_CPU_PROCESS_CPU_LOAD]]
    plt.plot(x, process_cpu_load, label=node_name + " Process CPU Load [%]")
    system_cpu_load = [y[1] * 100 for y in data[LOG_JVM_CPU_SYSTEM_CPU_LOAD]]
    plt.plot(x, process_cpu_load, label=node_name + " System CPU Load [%]")
    system_load_average = [y[1] * 100 for y in data[LOG_JVM_CPU_SYSTEM_LOAD_AVERAGE]]
    plt.plot(x, process_cpu_load, label=node_name + " System Load Average [%]")

def plotZookeeperMaxLatency(data, node_name="", x0=None):
    if x0 is None:
        x0 = data[LOG_JVM_ZK_MAXREQUESTLATENCY][0][0];
    x = [(x[0]- x0) / (1000) for x in data[LOG_JVM_ZK_MAXREQUESTLATENCY]]

    max_latency = [y[1] for y in data[LOG_JVM_ZK_MAXREQUESTLATENCY]]
    plt.plot(x, max_latency, label=node_name + " Max Latency [ms]")

def plotZookeeperPacketsPerInterval(data, node_name="", color="r", x0=None):
    if x0 is None:
        x0 = data[LOG_JVM_ZK_PACKETSRECEIVED][0][0];
    x = [(x[0]- x0) / (1000) for x in data[LOG_JVM_ZK_PACKETSRECEIVED]]

    packets_received = data[LOG_JVM_ZK_PACKETSRECEIVED]
    packets_sent = data[LOG_JVM_ZK_PACKETSSENT]

    packets_received_per_interval = discrete_derivative(packets_received, x0)
    packets_sent_per_interval = discrete_derivative(packets_sent, x0)

    if len(packets_received_per_interval) > 0:
        x, y = zip(*packets_received_per_interval)
        plt.scatter(x, y, marker="D", color=color, label=node_name + " packets received")

    if len(packets_sent_per_interval) > 0:
        x, y = zip(*packets_sent_per_interval)
        plt.scatter(x, y, marker="o", color=color, label=node_name + " packets sent")

def plotGcMoments(data, node_name="", x0=None, color='r'):
    if x0 is None:
        x0 = data[LOG_JVM_GC_PARNEW_COUNT][0][0];

    parnew_count = data[LOG_JVM_GC_PARNEW_COUNT]
    cms_count = data[LOG_JVM_GC_CONCURRENTMARKSWEEP_COUNT]

    # last_parnew = parnew_count[0][1]
    # last_cms = cms_count[0][1]
    # parnew_events = []
    # cms_events = []
    #
    # for i in range(len(parnew_count)):
    #
    #     delta_parnew = parnew_count[i][1] - last_parnew
    #     delta_cms = cms_count[i][1] - last_cms
    #
    #     if (delta_cms > 0):
    #         x = ((cms_count[i][0] - x0) / 1000.0)
    #         cms_events.append((x, int(delta_cms)))
    #
    #     if (delta_parnew > 0):
    #         x = ((parnew_count[i][0] - x0) / 1000.0)
    #         parnew_events.append((x, int(delta_parnew)))
    #
    #     last_parnew = parnew_count[i][1]
    #     last_cms = cms_count[i][1]

    cms_events = discrete_derivative(cms_count, x0)
    parnew_events = discrete_derivative(parnew_count, x0)

    if len(cms_events) > 0:
        x, y = zip(*cms_events)
        plt.scatter(x, y, marker="D", color=color, label=node_name + " GC (Concurrent Mark Sweep) [times]")

    if len(parnew_events) > 0:
        x, y = zip(*parnew_events)
        plt.scatter(x, y, marker="o", color=color, label=node_name + " GC (Par New) [times]")

def plotTimeSpentInGC(data, node_name="", x0=None, color='r'):
    if x0 is None:
        x0 = data[LOG_JVM_GC_PARNEW_TIME][0][0];

    parnew_time = data[LOG_JVM_GC_PARNEW_TIME]
    cms_time = data[LOG_JVM_GC_CONCURRENTMARKSWEEP_TIME]

    cms_events = discrete_derivative(cms_time, x0)
    parnew_events = discrete_derivative(parnew_time, x0)

    if len(cms_events) > 0:
        x, y = zip(*cms_events)
        plt.scatter(x, y, marker="D", color=color, label=node_name + " GC (Concurrent Mark Sweep) [ms]")

    if len(parnew_events) > 0:
        x, y = zip(*parnew_events)
        plt.scatter(x, y, marker="o", color=color, label=node_name + " GC (Par New) [ms]")


def discrete_derivative(data, x0=0):

    last_y = data[0][1]
    nonzero_derivative_points = []

    for i in range(len(data)):

        delta_y = int(data[i][1] - last_y)

        if (delta_y != 0):
            x = (data[i][0] - x0) / 1000.0
            nonzero_derivative_points.append((x, (delta_y)))

        last_y = data[i][1]

    return nonzero_derivative_points


#########################
#Code executed directly #
#########################
if __name__ == "__main__":

    LOG_FILE_DIRECTORY = "scratch/readers-vs-latency-10min-240/jmx";


    plt.title("JVM Heap")
    plt.xlabel("Seconds since start")
    plt.ylabel("Size [Mb]")

    data = readKeyLog(LOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_MEMORY))
    plotHeapUsage(data, "Kafka node 2", used=True)
    data = readKeyLog(LOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_MEMORY))
    plotHeapUsage(data, "Kafka node 1", used=True)
    data = readKeyLog(LOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_MEMORY))
    plotHeapUsage(data, "Kafka node 3", used=True)

    data = readKeyLog(LOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_2, LOG_TYPE_MEMORY))
    plotHeapUsage(data, "ZK node 2", used=True)
    data = readKeyLog(LOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_1, LOG_TYPE_MEMORY))
    plotHeapUsage(data, "ZK node 1", used=True)
    data = readKeyLog(LOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_3, LOG_TYPE_MEMORY))
    plotHeapUsage(data, "ZK node 3", used=True)

    plt.legend(prop={'size':12})

    plt.figure();

    plt.title("Cluster CPU Load")
    plt.xlabel("Seconds since start")
    plt.ylabel("Load Percentage")

    data = readKeyLog(LOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_CPU))
    plotCpuUsage(data, "Kafka node 2")
    data = readKeyLog(LOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_CPU))
    plotCpuUsage(data, "Kafka node 1")
    data = readKeyLog(LOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_CPU))
    plotCpuUsage(data, "Kafka node 3")

    plt.legend(prop={'size':12})

    plt.figure();

    plt.title("Garbage Collection")
    plt.xlabel("Seconds since start")
    plt.ylabel("Number of GC's performed during measurement interval")

    data = readKeyLog(LOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
    plotGcMoments(data, "Kafka node 2", color = 'g')
    data = readKeyLog(LOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
    plotGcMoments(data, "Kafka node 1", color = 'r')
    data = readKeyLog(LOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
    plotGcMoments(data, "Kafka node 3", color = 'b')

    plt.gca().yaxis.set_major_locator(MaxNLocator(integer=True))
    plt.legend(prop={'size':12})

    plt.show()