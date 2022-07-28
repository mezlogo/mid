package mezlogo.mid.cli;

import mezlogo.mid.api.model.HostAndPort;
import picocli.CommandLine;

public class HostAndPortConvertor implements CommandLine.ITypeConverter<HostAndPort> {
    @Override
    public HostAndPort convert(String value) {
        int pos = value.lastIndexOf(':');
        if (pos < 0) {
            throw new CommandLine.TypeConversionException("Invalid format: must be 'host:port' but was '" + value + "'");
        }
        String adr = value.substring(0, pos);
        int port = Integer.parseInt(value.substring(pos + 1));
        return new HostAndPort(adr, port);
    }
}
