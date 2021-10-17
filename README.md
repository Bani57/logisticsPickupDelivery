# Pickup and Delivery Problem PDP 
- Also known as Constrained (multiple) Travelling Salesman Problem
- Logistic company with a fleet of trucks
- Goal 1: satisfy customer requests: loads have to be transported from their origin location to their delivery point
- Goal 2: optimize profit
- Several constraints can be added to the problem, such as the costs of the vehicles and crews or the fuel capacities


## Environment
- The topology is a graph made of cities (nodes) connected by roads (edges)


## Satisfying Customer Requests
#### Customer requests:
- Tasks are spread over the topology
#### A transportation task is a data structure containing the following information:
- Pickup city
- Delivery city
- Weight in kg
- Reward in CHF


## Lifecycles and Companies
#### Logistic companies (1 or more)
- Own one or more trucks
- Fulfill customer requests
#### Lifecycle
- Working without interruption until all tasks delivered
- Exception: reactive agent will travel all the time


## Vehicles
- Fixed load capacity
- One or more tasks at a time
- Starting place
- Obliged to deliver a task to its destination
- Cost for a specific task: _function_(route)


## Planners/Behaviors
- «Brain» of the intelligent agents
- Decide what to do at every time step
- 4 planners to implement -> 4 exercises
#### Vehicle planners:
- Reactive planner/behavior for a reactive agent;
- Deliberative planner/behavior for a deliberative agent;
#### Company planners:
- Centralized planner of cooperative agents;
- Decentralized planner of self-interested agents.


## LogistPlatform
- A simulation platform for the Pickup and Delivery Problem
- Implements the PDP as presented
- Built on RePast:
  - Discrete scheduler
  - Dynamic visualization
- 3 configuration files: topology.xml, reactive.xml, tasks.xml
  - topology configuration file specifies the routes 
  - tasks configuration file specifies probabilities/tasks
  - reactive.xml (deliberative.xml, ...) specifies the framework setup, for example the classpath to behaviors, number of agents and their parameters
  
## Repository description
```bash
logisticPickupDelivery
├── auction
│   ├── agents
│   ├── config
|   |   ├── topology 
|   |   |   └── ...
|   |   ├── agents.xml
|   |   ├── auction.xml
|   |   ├── auction2.xml
|   |   └── settings_auction.xml
│   ├── doc
|   |   ├── auction.pdf # description of the task
|   |   └── report.pdf # description of the solution we implemented for the problem
│   ├── tournament
|   |   └── ... # tournament configurations
│   └── src
|       └── ... # implementation of our reactive agent
|
├── centralized
│   ├── agents
│   ├── config
|   |   ├── topology
|   |   |   └── ...
|   |   ├── agents.xml
|   |   ├── centralized.xml
|   |   └── settings_centralized.xml
│   ├── doc
|   |   ├── centralized.pdf # description of the task
|   |   └── report.pdf # description of the solution we implemented for the problem
│   └── src
|       └── ... # implementation of our reactive agent
|
├── deliberative
│   ├── agents
│   ├── config
|   |   └── topology
|   |   |   |   └── ...
|   |   ├── agents.xml
|   |   └── settings_deliberative.xml
│   ├── doc
|   |   ├── deliberative.pdf # description of the task
|   |   └── report.pdf # description of the solution we implemented for the problem
│   └── src
|       └── ... # implementation of our deliberative agent
|
├── reactive
|   ├── config
|   |   └── topology
|   |   |   └── ...
|   |   ├── agents.xml
|   |   ├── reactive.xml
|   |   ├── reactive2.xml
|   |   └── settings_reactive.xml
|   ├── doc
|   |   ├── reactive.pdf # description of the task
|   |   └── report.pdf # description of the solution we implemented for the problem
|   └── src
|       └── ... # implementation of our reactive agent
├── logist
│   └── ... # logist library
|
└── README.md
```

Every subfolder has nearly the same structure with some slight differences. Each of them contains a:
- config, where you can find the configuration files of the agents, of the task and of the topologies;
- src, where you can find the code that implements the behaviour of the agents described in the configuration files;
- doc, where you can find both a description of the exercise, which has the same name of the exercise, and a report, a summary of the of the formulas and the logic behind the solution we implemented.

Additionally, the auction directory contains 2 more subfolders:
- agents, that contains the agents compiled
- tournament, that contains some tournament configurations

Indeed, the agent we submitted for this exercise was supposed to compete in a tournament with the bidder agents written by the other course attendants.
