FROM fedora:44

ENV JAVA_HOME=/usr/lib/jvm/java-25-openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Copy entrypoint script
COPY .devcontainer/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# Copy repository into the container
WORKDIR /workspaces/ats_service
COPY . .

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]