package com.amict.blockmonitor.api;

import com.amict.blockmonitor.BlockMonitor;
import com.flowpowered.math.vector.Vector3i;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.persistence.DataFormat;
import org.spongepowered.api.data.persistence.DataFormats;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.animal.Wolf;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.extent.Extent;

import javax.swing.text.html.Option;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Created by johnfg10 on 04/06/2017.
 */
public class Record {
    DataContainer dataContainer;
    EventType eventType;
    Optional<Location> worldLocationOptional;

    @Inject
    private Logger logger;

    public Record(){
        this.dataContainer = DataContainer.createNew();
        this.eventType = EventType.Uknown;
        this.worldLocationOptional = Optional.empty();

    }

    public void setCause(Event event){
        Cause cause = event.getCause();
        LocalDateTime localDateTime = LocalDateTime.now();

        if (event instanceof ClientConnectionEvent.Join){
            //we know its a client connect join event
            ClientConnectionEvent.Join clientConnectionEventJoin = (ClientConnectionEvent.Join) event;
            this.eventType = EventType.ConnectionEvent;
            Optional<Location> locationOptional = getLocationFromCause(cause);
            locationOptional.ifPresent(location -> worldLocationOptional = Optional.of(location));
            writePlayerData(clientConnectionEventJoin.getTargetEntity());
        }

        if (event instanceof ClientConnectionEvent.Disconnect){
            //we know its a client connect disconnect event
            ClientConnectionEvent.Disconnect clientConnectionEventDisconnect = (ClientConnectionEvent.Disconnect) event;
            this.eventType = EventType.DisconnectionEvent;
            Optional<Location> locationOptional = getLocationFromCause(cause);
            locationOptional.ifPresent(location -> worldLocationOptional = Optional.of(location));
            writePlayerData(clientConnectionEventDisconnect.getTargetEntity());
        }

        if (event instanceof ChangeBlockEvent.Break){
            ChangeBlockEvent.Break changeBlockEventBreak = (ChangeBlockEvent.Break) event;
        }

        if (event instanceof ChangeBlockEvent.Place){
            ChangeBlockEvent.Place changeBlockEventPlace = (ChangeBlockEvent.Place) event;
        }
    }

    //helper methods

    /**
     * takes a blocksnapshot and writes it to the @DataContainer
     * @param blockSnapshot the block snapshot to write
     */
    public void writeBlockData(BlockSnapshot blockSnapshot){
        //if location data exists
        if (blockSnapshot.getLocation().isPresent()){
            Location<World> worldLocation = blockSnapshot.getLocation().get();
            this.dataContainer.set(DataQuery.of("block", "location"), worldLocation.toString());
        }
        this.dataContainer.set(DataQuery.of("block", "blockcontainer"), blockSnapshot.toContainer());
    }

    public Optional<Location> getLocationFromCause(Cause cause){
        Optional<Location> locationOptional = cause.first(Location.class);
        if (locationOptional.isPresent()){
            Location location = locationOptional.get();
            return Optional.of(location);
        }else {
            logger.info("world location could not be found");
        }
        return Optional.empty();
    }

    public void writePlayerData(Player player){
        this.dataContainer.set(DataQuery.of("player", "uuid"), player.getUniqueId());
        this.dataContainer.set(DataQuery.of("player", "username"), player.getName());
        this.dataContainer.set(DataQuery.of("player", "location"), player.getLocation().createSnapshot().toContainer());
        this.dataContainer.set(DataQuery.of("player", "ipaddress"), player.getConnection().getAddress());
    }

    //public DataContainer writeBlockSnapshot

    public void submitToDatabase(){
        String prepareStatementString = "INSERT INTO `blockmonitor` (`locationX`,`locationY`,`locationZ`,`worldName`,`eventtype`,`datacontiner` ) VALUES (?, ?, ?, ?, ?, ?)";
        try {
            DataFormat dataFormat = DataFormats.JSON;
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            dataFormat.writeTo(outputStream, dataContainer);
            String dataContainerAsJson = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);

            Connection connection = BlockMonitor.storageHandler.dataSource.getConnection();
            PreparedStatement prepareStatement = connection.prepareStatement(prepareStatementString);
            if (worldLocationOptional.isPresent()) {
                Location worldLocation = worldLocationOptional.get();
                prepareStatement.setInt(1, worldLocation.getBlockX());
                prepareStatement.setInt(2, worldLocation.getBlockY());
                prepareStatement.setInt(3, worldLocation.getBlockZ());
                World world = (World) worldLocation.getExtent();
                if (world != null)
                    prepareStatement.setString(4, world.getName());
            }
            prepareStatement.setString(5, eventType.toString());
            prepareStatement.setString(6, dataContainerAsJson);
            //System.out.println("did excecute correctly: " + prepareStatement.execute());
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

}
