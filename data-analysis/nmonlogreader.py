import matplotlib.pyplot as plt
from dateutil.parser import parse

NMON_NET_READ = "read"
NMON_NET_WRITE = "write"

def getFilename(name, cluster, type):
    return name + "-" + cluster + "-" + type + ".log"

def getNetworkKeyName(interface, read_or_write):
    return interface + "-" + read_or_write + "-KB/s"

def readNmonLog(filename, read_network=True, read_disk=True):
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


        for line in content:
            line_parts = line.strip("\n").split(",")
            type = line_parts[0]

            #Timestamp of the next measurement
            if (type == "ZZZZ"):
                dt = parse(line_parts[2])
                current_time = int(dt.strftime("%s"))
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
        x0 = float(data['TIME'][0]);
    x = [(x - x0) for x in data['TIME']]

    network_read = data['NET'][getNetworkKeyName(interface, NMON_NET_READ)]
    plt.plot(x, network_read, label=node_name + " " + interface + " read [KB/s]")
    network_read = data['NET'][getNetworkKeyName(interface, NMON_NET_WRITE)]
    plt.plot(x, network_read, label=node_name + " " + interface + " write [KB/s]")

def plotDiskUsage(data, node_name, disk, x0=None):
    if x0 is None:
        x0 = float(data['TIME'][0]);
    x = [(x - x0) for x in data['TIME']]

    disk_read = data['DISKREAD'][disk]
    plt.plot(x, disk_read, label=node_name + " " + disk + " read [KB/s]")
    disk_write = data['DISKWRITE'][disk]
    plt.plot(x, disk_write, label=node_name + " " + disk + " write [KB/s]")

def plotDiskBusy(data, node_name, disk, x0=None):
    if x0 is None:
        x0 = float(data['TIME'][0]);
    x = [(x - x0) for x in data['TIME']]

    disk_busy = data['DISKBUSY'][disk]
    plt.plot(x, disk_busy, label=node_name + " " + disk + " busy [%]")

#########################
#Code executed directly #
#########################
if __name__ == "__main__":

    LOG_FILE = "scratch/example.nmon";

    data = readNmonLog(LOG_FILE)

    plt.title("Network/Disk usage")
    plt.xlabel("Seconds since start")
    plt.ylabel("Rate [KB/s]")

    plotNetworkUsage(data, "Kafka 1", 'wlan0')
    plotDiskUsage(data, "Kafka 1", "sda")

    plt.legend(prop={'size':12})

    plt.twinx()

    plotDiskBusy(data, "Kafka 1", "sda")

    plt.legend(prop={'size':12})

    plt.show()