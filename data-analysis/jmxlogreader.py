import matplotlib.pyplot as plt

LOG_TYPE_MEMORY = "mem"
LOG_TYPE_CPU = "cpu"
LOG_TYPE_BYTES_IN_PER_SECOND = "bips"
LOG_TYPE_BYTES_OUT_PER_SECOND = "bops"
LOG_TYPE_MESSAGES_IN_PER_SECOND = "mips"
LOG_TYPE_GENERAL = "general"

CLUSTER_NODE_1 = "c1"
CLUSTER_NODE_2 = "c2"
CLUSTER_NODE_3 = "c3"

LOG_JVM_KAFKA = "kafka"
LOG_JVM_ZOOKEEPER = "zoo"

LOG_JVM_MEM_HEAP_USED = "HeapMemoryUsage_used"
LOG_JVM_MEM_HEAP_INIT = "HeapMemoryUsage_init"
LOG_JVM_MEM_HEAP_MAX = "HeapMemoryUsage_max"
LOG_JVM_MEM_HEAP_COMMITTED = "HeapMemoryUsage_committed"

LOG_JVM_CPU_SYSTEM_LOAD_AVERAGE = "SystemLoadAverage"
LOG_JVM_CPU_SYSTEM_CPU_LOAD = "SystemCpuLoad"
LOG_JVM_CPU_PROCESS_CPU_LOAD = "ProcessCpuLoad"

def getFilename(name, cluster, type):
    return name + "-" + cluster + "-" + type + ".log"

def readKeyLog(filename, keys=[], start = 0):
    data = {}

    with open(filename) as f:
        content = f.readlines()

        for line in content:
            parts = line.strip("\n").split("\t")
            keyParts = parts[0].split(".")
            key = keyParts[-1]

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




#########################
#Code executed directly #
#########################
if __name__ == "__main__":

    LOG_FILE_DIRECTORY = "scratch/MMTB-1w19c200ups1000ts60000x/logs";


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

    plt.show()