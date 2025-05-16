# JRobin to RRDtool Converter

This tool can be used to convert [JRobin](https://sourceforge.net/projects/jrobin/) time series data into [RRDtool](https://oss.oetiker.ch/rrdtool) compatible format.
The tool was build in the [OpenNMS Project](https://www.opennms.com).

> [!IMPORTANT]
> It is not possible to convert from different OpenNMS storage strategies like `storeByGroup` and `storeByForeignSource`.
> Make sure you use the same settings as the origin OpenNMS with JRobin.
> It is also *not* possible to change internal RRD data structure i.e. 'RRA' or 'step'.
> The 'RRA' configuration in JRobin and RRDtool *must* be the same.

## 👮 Requirements

This tool has the following requirements

* Java 17 runtime environment (JRE) as [OpenJDK](https://openjdk.java.net/)
* RRDtool has to be installed
* Build from source requires Java Development Kit (JDK) and [Maven](https://maven.apache.org/download.cgi)
* Make sure `mvn`, `java` and `javac` binary are in your search path
* Verify JRE version with `java -version` and Java compiler version with `javac -version` which should be `17`

> [!IMPORTANT]
> You need enough additional free disk space for the converter, JRobin files are dumped to XML and reimported into RRDtool.
> You may need up to **13 to 16 times** the space of your current JRobin file size.
> This tool does not modify existing JRobin files, anyway make always a backup.
> By default a RRD file directory size with 26 MB will use 340 MB.
> The XML RRD dump files and the JRobin files are still on the disk.
> A user needs to delete these files manually.

## 👩‍🏭 Build from source

The following steps describe how to build the tool from source.

**Step 1:** Checkout the source code
```
git clone https://github.com/opennms-forge/jrobin-to-rrdtool-converter.git
```

**Step 2:** Change directory
```
cd jrobin-to-rrdtool-converter
```

**Step 3:** Compile and assemble runnable jar
```
make
```

**Step 4:** Execute tool as runnable jar
```
cd target
java -jar convertjrb-1.1.0-SNAPSHOT-jar-with-dependencies.jar
```

## 🕹️ Usage

The converter has one argument with the path to OpenNMS JRobin files and has two additional options.

* `-rrdtool`: Location of the `rrdtool` binary which can be located with `which rrdtool`.
  If this option is not set the default is set to `/usr/local/bin/rrdtool`.
* `-threads`: Number of threads to convert JRobin files into RRDtool format.
  If this option is not set, the default value is `4`.
* `<path/to/jrobin-files>` is the path for the tool to search for existing JRobin files.

The convertion is not destructive for the existing JRobin files.
Subdirectories and file names will be preserved.

> [!IMPORTANT]
> JRobin files are **not** deleted and still exist.
> The exchange XML data has to be manually cleaned up, e.g. `find . -iname $OPENNMS_HOME/share/rrd/*.xml --exec rm -rf {} \;`.

> [!TIP]
> Backup your `/opt/opennms/share/rrd` or `/usr/share/opennms/share/rrd` directory so that you have a way to rollback in case you did a mistake.

## 🚀 Example with a native installation

Here is an example running the tool on the same server as the Horizon Core server with JRobin files in `/opt/opennms/share/rrd`.
If you have a DEB-based installation the default directory is `/usr/share/opennms/share/rrd`.
The directory structure is preserved during the migration.

**Step 1:** Create a directory for the tool
```
sudo mkdir -p /opt/jrb2rrd
```

**Step 2:** Download the tool from the GitHub release
```
cd /opt/jrb2rrd
sudo wget -P /opt/jrb2rrd https://github.com/opennms-forge/jrobin-to-rrdtool-converter/releases/download/v1.1.0/convertjrb-1.1.0.tar.gz
sudo tar -xzf convertjrb-1.1.0.tar.gz
```

**Step 3:** Stop OpenNMS Horizon Core service to prevent writing to JRobin files during the conversion.

```
sudo systemctl stop opennms
```

**Step 4:** Convert the file with the user `opennms`

```
sudo -u opennms java -jar convertjrb-1.1.0-jar-with-dependencies.jar \
  -rrdtool /usr/bin/rrdtool \
  -threads 8 \
  /opt/opennms/share/rrd
```

The RRD files are created in the exact same directory structure as the JRobin files.

**Step 5:** Configure OpenNMS Horizon using RRDTool following the instructions in the OpenNMS Horizon [Timeseries Storage](https://docs.opennms.com/horizon/latest/deployment/time-series-storage/timeseries/rrdtool.html) documentation.

**Step 6:** Start OpenNMS Horizon core

```
sudo systemctl start opennms
```

**Step 7:** Verify in resource graphs for nodes if you have access to historical data.

**Step 8:** Delete JRobin and XML files after successful migration

```
sudo find /opt/opennms/share/rrd -name "*.jrb" -exec rm {} \;
sudo find /opt/opennms/share/rrd -name "*.xml" -exec rm {} \;
```

## 🐳 Example running with Docker

Change into the directory where your JRobin files (*.jrb) are located.
Replace the path `/opt/opennms` with `/usr/share/opennms` if you are on a DEB-based operating system.  

> [!TIP]
> Replace OPENNMS_HOME in DEB-based systems with `/usr/share/opennms` and on RPM-based distributions with `/opt/opennms`.

**Step 1:** Stop OpenNMS Horizon Core service to prevent writing to JRobin files during the conversion.

```
sudo systemctl stop opennms
```

**Step 2:** Change into the directory with the RRD files
```
cd /opt/opennms/share/rrd
```

**Step 3:** Run the converter with docker
```
sudo docker run --rm -v "$(pwd):/data" ghcr.io/opennms-forge/jrobin-to-rrdtool-converter:latest
```

> [!TIP]
> If you want to run it with more threads than 4, run it with `docker run --rm -v "$(pwd):/data" ghcr.io/opennms-forge/jrobin-to-rrdtool-converter:latest -threads 8 /data`

**Step 4:** Set the permissions for the OpenNMS user

```
sudo /opt/opennms/bin/fix-permissions
```

**Step 5:** Configure OpenNMS Horizon using RRDTool following the instructions in the OpenNMS Horizon [Timeseries Storage](https://docs.opennms.com/horizon/latest/deployment/time-series-storage/timeseries/rrdtool.html) documentation.

**Step 6:** Start OpenNMS Horizon core

```
sudo systemctl start opennms
```

**Step 7:** Verify in resource graphs for nodes if you have access to historical data.

**Step 8:** Delete JRobin and XML files after successful migration

```
sudo find /opt/opennms/share/rrd -name "*.jrb" -exec rm {} \;
sudo find /opt/opennms/share/rrd -name "*.xml" -exec rm {} \;
```
