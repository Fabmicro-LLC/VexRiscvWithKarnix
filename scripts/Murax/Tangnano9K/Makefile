

VERILOG = ../../../Murax.v toplevel.v

generate: ../../../MuraxForTangnano9KTopLevel.hex
	(cd ../../..; sbt "runMain vexriscv.demo.MuraxForTangnano9K")

sim:
	(cd ../../..; sbt "runMain vexriscv.demo.MuraxForTangnano9KSim")

../../../MuraxForTangnano9KTopLevel.v :
	(cd ../../..; sbt "runMain vexriscv.demo.MuraxForTangnano9K")


../../../MuraxForTangnano9KTopLevel.hex:
	(cd ../../../src/main/c/murax_tangnano9k/hello_world; $(MAKE))
	cp ../../../src/main/c/murax_tangnano9k/hello_world/build/hello_world.hex ../../../MuraxForTangnano9KTopLevel.hex


bin/MuraxForTangnano9KTopLevel.json : ${VERILOG} ../../../MuraxForTangnano9KTopLevel.hex
	mkdir -p bin
	#yosys -p "synth_gowin -abc9 -top MuraxForTangnano9KTopLevel -json bin/MuraxForTangnano9KTopLevel.json" ${VERILOG}
	yosys -L yosys.log -p "synth_gowin -top toplevel -json bin/MuraxForTangnano9KTopLevel.json" ${VERILOG}

bin/MuraxForTangnano9KTopLevel.pack : tangnano9k.cst bin/MuraxForTangnano9KTopLevel.json
	nextpnr-gowin --cst tangnano9k.cst --placed-svg bin/MuraxForTangnano9KTopLevel_placed.svg --device GW1NR-LV9QN88PC6/I5 --json bin/MuraxForTangnano9KTopLevel.json --log nextpnr-gowin.log --write $@

bin/MuraxForTangnano9KTopLevel.fs : bin/MuraxForTangnano9KTopLevel.pack
	gowin_pack -d GW1N-9C -o $@ $^

compile : bin/MuraxForTangnano9KTopLevel.fs


clean :
	rm -f bin/* ../../../MuraxForTangnano9KTopLevel*


