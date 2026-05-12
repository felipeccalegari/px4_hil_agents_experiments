FROM ros:humble-ros-base

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    ros-humble-mavros \
    ros-humble-mavros-extras \
    ros-humble-geographic-msgs \
    ros-humble-rosbridge-suite \
    ros-humble-rosbridge-server \
    python3-pip \
    python3-colcon-common-extensions \
    iputils-ping \
    net-tools \
    && rm -rf /var/lib/apt/lists/*

RUN /opt/ros/humble/lib/mavros/install_geographiclib_datasets.sh

RUN echo "source /opt/ros/humble/setup.bash" >> /root/.bashrc

COPY start.sh /root/start.sh
RUN chmod +x /root/start.sh

CMD ["/bin/bash"]