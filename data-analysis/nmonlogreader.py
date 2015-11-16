import matplotlib.pyplot as plt
from os import listdir
from os.path import isfile, join, isdir
from dateutil.parser import parse

NMON_NET_READ = "read"
NMON_NET_WRITE = "write"

NMON_TIMEZONE_CORRECTION = 9*3600

def getFilename(name, cluster, type):
    return name + "-" + cluster + "-" + type + ".log"

def getNetworkKeyName(interface, read_or_write):
    return interface + "-" + read_or_write + "-KB/s"

def readNmonDirectory(directory, read_network=True, read_disk=True):
    data = {}

    for filename in listdir(directory):
        pathToFile = directory + "/" + filename
        if (isfile(pathToFile)):
            nodename = filename.split("_")[0]
            data[nodename] = readNmonLog(pathToFile, read_network, read_disk)

    return data

def readNmonLog(filename, read_network=True, read_disk=True, read_cpu=True, read_mem=True):
    data = {}

    with open(filename) as f:
        content = f.readlines()
        current_time = 0;
        timestamps = []
        net_keys = []
        net_packets_keys = []
        disk_read_keys = []
        disk_write_keys = []
        disk_busy_keys = []
        cpu_keys = {}
        mem_keys = []

        for x in range(0,25):
            cpu_keys[x] = []

        for line in content:
            line_parts = line.strip("\n").split(",")
            type = line_parts[0]

            #Timestamp of the next measurement
            if (type == "ZZZZ"):
                dt = parse(line_parts[2])
                current_time = int(dt.strftime("%s"))+NMON_TIMEZONE_CORRECTION
                timestamps.append(current_time)

            if (type == 'NET' and read_network):
                net_keys = _processLineFloatValues("NET", data, line_parts, net_keys)

            if (type == "NETPACKET" and read_network):
                net_packets_keys = _processLineFloatValues("NETPACKET", data, line_parts, net_packets_keys)

            if (type == "DISKREAD" and read_disk):
                disk_read_keys = _processLineFloatValues("DISKREAD", data, line_parts, disk_read_keys)

            if (type == "DISKWRITE" and read_disk):
                disk_write_keys = _processLineFloatValues("DISKWRITE", data, line_parts, disk_write_keys)

            if (type == "DISKBUSY" and read_disk):
                disk_busy_keys = _processLineFloatValues("DISKBUSY", data, line_parts, disk_busy_keys)

            if (type == "MEM" and read_mem):
                mem_keys = _processLineFloatValues("MEM", data, line_parts, mem_keys)

            if (type[0:4] == "CPU0" and read_cpu):
                cpu_id = int(type[3:6])
                cpu_keys[cpu_id] = _processLineFloatValues("CPU-"+str(cpu_id), data, line_parts, cpu_keys[cpu_id])

        data['TIME'] = timestamps
        if (read_network):
            data['NET']['keys'] = filter(None, net_keys)

        if (read_disk):
            data['NET']['keys'] = filter(None, net_keys)

        return data

def _processLineFloatValues(name, data, line_parts, keys):
    if not data.has_key(name):
        keys = line_parts[2:]
        data[name] = {'raw':[line_parts[2:]]}
        for key in keys:
            if key:
                data[name][key] = []
    else:
        data[name]['raw'].append(line_parts[2:])

        for i in range(len(line_parts)-2):
            key = keys[i]
            value = line_parts[i+2]
            if key:
                if value:
                    data[name][key].append(float(value))
                else:
                    data[name][key].append(None)

    return keys

def plotNetworkUsage(data, node_name, interface, x0=None):
    if x0 is None:
        x0 = float(data['TIME'][0])*1000.0;
    x = [(x - (x0/1000.0)) for x in data['TIME']]

    network_read = data['NET'][getNetworkKeyName(interface, NMON_NET_READ)]
    plt.plot(x, network_read, label=node_name + " " + interface + " read [KB/s]", linestyle=":")
    network_read = data['NET'][getNetworkKeyName(interface, NMON_NET_WRITE)]
    plt.plot(x, network_read, label=node_name + " " + interface + " write [KB/s]")

def plotDiskUsage(data, node_name, disk, x0=None):
    if x0 is None:
        x0 = float(data['TIME'][0])*1000.0;
    x = [(x - (x0/1000.0)) for x in data['TIME']]

    disk_read = data['DISKREAD'][disk]
    plt.plot(x, disk_read, label=node_name + " " + disk + " read [KB/s]", linestyle=":")
    disk_write = data['DISKWRITE'][disk]
    plt.plot(x, disk_write, label=node_name + " " + disk + " write [KB/s]")

def plotDiskBusy(data, node_name, disk, x0=None):
    if x0 is None:
        x0 = float(data['TIME'][0])*1000.0;
    x = [(x - (x0/1000.0)) for x in data['TIME']]

    disk_busy = data['DISKBUSY'][disk]
    plt.plot(x, disk_busy, label=node_name + " " + disk + " busy [%]")

def plotCpuCoreUsage(data, x0=None):
    if x0 is None:
        x0 = float(data['TIME'][0])*1000.0;
    x = [(x - (x0/1000.0)) for x in data['TIME']]

    fig,ax = plt.subplots(4, 6, sharex=True, sharey=True)
    plt.subplots_adjust(left=0.03, bottom=0.05, right=0.98, top=0.98, wspace=0.02, hspace=0.02)

    iX=0
    iY=0
    for i in range(1,25):
        cpuUsage = data['CPU-'+str(i)]['User%']
        ax[iX, iY].plot(x, cpuUsage)

        if iX == 3:
            ax[iX,iY].set_xlabel("Time since start [s]")

        if iY == 0:
            ax[iX,iY].set_ylabel("Load [%]")

        iX += 1
        if iX >= 4:
            iX = 0
            iY += 1

def plotNmonMemUsage(nodes, x0=None):
    x = {}

    for nodename in nodes:
        data = nodes[nodename]
        if x0 is None:
            x0 = float(data['TIME'][0])*1000.0;
        x[nodename] = [(val - (x0/1000.0)) for val in data['TIME']]

    #memtotal,hightotal,lowtotal,swaptotal,memfree,highfree,lowfree,swapfree,memshared,cached,active,bigfree,buffers,swapcached,inactive
    fig,ax = plt.subplots(6, 1, sharex=True)
    plt.subplots_adjust(hspace=0.10)

    for nodename in nodes:
        data = nodes[nodename]
        ax[0].plot(x[nodename], data['MEM']['active'])
        ax[0].set_ylabel("Active")

    for nodename in nodes:
        data = nodes[nodename]
        ax[1].plot(x[nodename], data['MEM']['cached'])
        ax[1].set_ylabel("Cached")

    for nodename in nodes:
        data = nodes[nodename]
        ax[2].plot(x[nodename], data['MEM']['buffers'])
        ax[2].set_ylabel("Buffers")

    for nodename in nodes:
        data = nodes[nodename]
        ax[3].plot(x[nodename], data['MEM']['memfree'])
        ax[3].set_ylabel("Free")

    for nodename in nodes:
        data = nodes[nodename]
        ax[4].plot(x[nodename], data['MEM']['inactive'])
        ax[4].set_ylabel("inactive")

    for nodename in nodes:
        data = nodes[nodename]
        ax[5].plot(x[nodename], data['MEM']['swapfree'])
        ax[5].set_ylabel("Swapfree")


#########################
#Code executed directly #
#########################
if __name__ == "__main__":

    LOG_FILE = "scratch/test1/nmon/clusternode1_150901_0557.nmon";

    data = readNmonLog(LOG_FILE)

    plt.title("Network/Disk usage")
    plt.xlabel("Seconds since start")
    plt.ylabel("Rate [KB/s]")

    plotNetworkUsage(data, "Kafka 1", 'eth4')

    plt.legend(prop={'size':12})

    plt.twinx()

    #plotDiskBusy(data, "Kafka 1", "sda")

    plt.legend(prop={'size':12})

    plt.show()